(ns ^:eftest/synchronized
  bass4.test.reqs-attack
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.services.auth :as auth-service]
            [bass4.registration.services :as reg-service]
            [bass4.services.attack-detector :as a-d]
            [bass4.config :as config]))


(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  (fn [f]
    (db/clear-failed-logins!)
    (swap! a-d/blocked-ips (constantly {}))
    (swap! a-d/blocked-last-request (constantly {}))
    (swap! a-d/global-block (constantly nil))
    (swap! a-d/global-last-request (constantly nil))
    (fix-time
      (f))
    (db/clear-failed-logins!)))


(defn visit-params
  [state uri & params]
  (apply visit (into [state uri] (flatten params))))

(defn attack-uri
  ([state uri post-params attacks]
   (attack-uri state uri post-params attacks "localhost"))
  ([state uri post-params attacks from-ip-address]
   (loop [current-state state attacks attacks]
     (if (seq attacks)
       (let [[wait status] (first attacks)
             params (if (map? post-params)
                      [:params post-params]
                      post-params)]
         (-> current-state
             (advance-time-s! wait)
             (visit-params uri [:request-method :post :remote-addr from-ip-address] params)
             (has (status? status))
             ((fn [state]
                (if (= 302 (get-in state [:response :status]))
                  (follow-redirect state)
                  state)))
             (recur (rest attacks))))
       current-state))))

(def standard-attack
  (concat
    (repeat a-d/const-fails-until-ip-block [1 422])
    (repeat 3 [0 429])
    [[(dec a-d/const-ip-block-delay) 429]
     [1 422]
     [0 429]
     [a-d/const-attack-interval 422]]
    (repeat (dec a-d/const-fails-until-ip-block) [0 422])
    [[0 429]]))


(deftest attack-login
  (let [user-id (create-user-with-password! {"smsnumber" "00"})]
    (-> *s*
        (attack-uri
          "/login"
          {:username "%€#&()" :password "%€#&()"}
          standard-attack)
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 429))
        (advance-time-s! a-d/const-ip-block-delay)
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302)))))


(deftest attack-double-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"smsnumber" "00"})
          state   (-> *s*
                      (visit "/login" :request-method :post :params {:username user-id :password user-id})
                      (has (status? 302))
                      (follow-redirect)
                      (has (some-text? "666777")))]
      (attack-uri
        state
        "/double-auth"
        {:code "%€#&()"}
        standard-attack))))

(deftest attack-re-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"smsnumber" "00"})
          _       (link-user-to-treatment! user-id tx-autoaccess {})
          state   (-> *s*
                      (modify-session {:user-id user-id :double-authed? true})
                      (visit "/user")
                      (visit "/user/tx/messages")
                      (has (status? 200))
                      (modify-session {:auth-re-auth? true})
                      (visit "/user/tx/messages")
                      (has (status? 302))
                      (follow-redirect)
                      (has (some-text? "Authenticate again")))]
      (attack-uri
        state
        "/re-auth"
        {:password "%€#&()"}
        standard-attack))))

(deftest attack-api-re-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-treatment! tx-autoaccess)
          state   (-> *s*
                      (modify-session {:user-id user-id :double-authed? true})
                      (visit "/user")
                      (visit "/api/user/tx/messages")
                      (has (status? 200))
                      (modify-session {:auth-re-auth? true})
                      (visit "/api/user/tx/messages")
                      (has (status? 440)))]
      (attack-uri
        state
        "/api/re-auth"
        [:body-params {:password "%€#&()"}]
        standard-attack))))

(deftest attack-re-auth-ajax
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-treatment! tx-autoaccess)
          state   (-> *s*
                      (modify-session {:user-id user-id :double-authed? true})
                      (visit "/user")
                      (visit "/user/tx/messages")
                      (has (status? 200))
                      (modify-session {:auth-re-auth? true})
                      (visit "/user/tx/messages")
                      (has (status? 302))
                      (follow-redirect)
                      (has (some-text? "Authenticate again")))]
      (attack-uri
        state
        "/re-auth-ajax"
        {:password "%€#&()"}
        standard-attack))))

(deftest attack-login-double-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"smsnumber" "00"})]
      (link-user-to-treatment! user-id tx-autoaccess {})
      (-> *s*
          (attack-uri
            "/login"
            {:username user-id :password "%€#&()"}
            (repeat 1 [0 422]))
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "666777"))
          (attack-uri
            "/double-auth"
            {:code "%€#&()"}
            (concat
              (repeat (dec a-d/const-ip-block-delay) [0 422])
              [[0 429]
               [0 429]
               [0 429]
               [(dec a-d/const-ip-block-delay) 429]
               [1 422]]))
          (advance-time-s! a-d/const-ip-block-delay)
          (visit "/double-auth" :request-method :post :params {:code "666777"})
          (has (status? 302))
          (follow-redirect)
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/tx/messages")
          (has (status? 302))
          (attack-uri
            "/re-auth-ajax"
            {:password "%€#&()"}
            (concat
              (repeat 10 [0 422])
              (repeat 10 [0 429])))
          (advance-time-s! a-d/const-ip-block-delay)
          (visit "/re-auth-ajax" :request-method :post :params {:password user-id})
          (has (status? 200))))))

(deftest attack-parallel
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"smsnumber" "00"})]
      (-> *s*
          (attack-uri
            "/login"
            {:username user-id :password "%€#&()"}
            [[0 422]])
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "666777"))
          (attack-uri
            "/double-auth"
            {:code "%€#&()"}
            [[70 422]
             [70 422]])))
    (-> *s*
        (attack-uri
          "/login"
          {:username "%€#&()" :password "%€#&()"}
          (concat
            (repeat
              (- a-d/const-fails-until-ip-block 3)
              [0 422])
            [[0 429]
             [a-d/const-ip-block-delay 422]])))))

(deftest attack-parallel-different-ips
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"smsnumber" "00"})]
      (-> *s*
          (attack-uri
            "/login"
            {:username user-id :password "%€#&()"}
            [[0 422]])
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "666777"))
          (attack-uri
            "/double-auth"
            {:code "%€#&()"}
            [[70 422]
             [70 422]])))
    (-> *s*
        (attack-uri
          "/login"
          {:username "%€#&()" :password "%€#&()"}
          standard-attack
          "192.168.0.1"))))


(deftest attack-registration
  (with-redefs [captcha/captcha!                (constantly {:filename "%€#&()" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:first-name :last-name}
                                                             :group                  570281 ;;No assessments in this group
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :auto-id-prefix         "xxx-"
                                                             :auto-id-length         3
                                                             :auto-id?               true})]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (attack-uri
          "/registration/564610/captcha"
          {:captcha "%€#&()"}
          (concat
            (repeat 4 [0 422])
            [[0 302]]
            (repeat 4 [0 422])
            [[0 302]]
            (repeat
              (- a-d/const-fails-until-ip-block 8)
              [0 422])
            [[1 429]]))
        (advance-time-s! 9)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (follow-redirect)
        (has (some-text? "Enter your"))
        (visit "/registration/564610/form" :request-method :post :params {:first-name "Lasse" :last-name "Basse"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "we promise")))))

(deftest attack-global
  (dotimes [ip-address 10]
    (attack-uri
      *s*
      "/login"
      {:username "%€#&()" :password "%€#&()"}
      (repeat (/ a-d/const-fails-until-global-block 10) [1 422])
      (str ip-address)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "%€#&()" :password "%€#&()"} :remote-addr "hejsan")
      (has (status? 429)))
  (advance-time-s! a-d/const-global-block-delay)
  (-> *s*
      (visit "/login" :request-method :post :params {:username "%€#&()" :password "%€#&()"} :remote-addr "hoppsan")
      (has (status? 422)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "%€#&()" :password "%€#&()"} :remote-addr "tjosan")
      (has (status? 429)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "%€#&()" :password "%€#&()"} :remote-addr "METALLICA")
      (has (status? 429)))
  (advance-time-s! a-d/const-global-block-delay)
  (-> *s*
      (visit "/login" :request-method :post :params {:username "%€#&()" :password "%€#&()"} :remote-addr "SLAYER")
      (has (status? 422)))
  (advance-time-s! a-d/const-attack-interval)
  (dotimes [ip-address 5]
    (attack-uri
      *s*
      "/login"
      {:username "%€#&()" :password "%€#&()"}
      (repeat (/ a-d/const-fails-until-global-block 10) [1 422])
      (str ip-address))))


(deftest attack-quick-login
  (dotimes [_ 10]
    (-> *s*
        (visit "/q/xx" :request-method :get)
        (has (status? 200))))
  (-> *s*
      (visit "/q/xx" :request-method :get)
      (has (status? 429))))