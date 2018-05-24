(ns bass4.test.reqs-attack
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     debug-headers-not-text?
                                     log-return
                                     log-body
                                     *s*
                                     advance-time-s!
                                     fix-time]]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.services.auth :as auth-service]
            [bass4.middleware.core :as mw]
            [bass4.services.registration :as reg-service]
            [bass4.services.attack-detector :as a-d]))


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



(defn attack-uri
  ([state uri post attacks]
   (attack-uri state uri post attacks "localhost"))
  ([state uri post attacks from-ip-address]
   (loop [current-state state attacks attacks]
     (if (seq attacks)
       (let [[wait status] (first attacks)]
         (-> current-state
             (advance-time-s! wait)
             (visit uri :request-method :post :params post :remote-addr from-ip-address)
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
  (-> *s*
      (attack-uri
        "/login"
        {:username "xxx" :password "xxx"}
        standard-attack)
      (visit "/login" :request-method :post :params {:username 536975 :password 536975})
      (has (status? 429))
      (advance-time-s! a-d/const-ip-block-delay)
      (visit "/login" :request-method :post :params {:username 536975 :password 536975})
      (has (status? 302))))


(deftest attack-double-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [state (-> *s*
                    (visit "/login" :request-method :post :params {:username 536975 :password 536975})
                    (has (status? 302))
                    (follow-redirect)
                    (has (some-text? "666777")))]
      (attack-uri
        state
        "/double-auth"
        {:code "xxx"}
        standard-attack))))

(deftest attack-re-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [state (-> *s*
                    (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
                    (visit "/user/messages")
                    (has (status? 200))
                    (visit "/debug/set-session" :params {:auth-re-auth true})
                    (visit "/user/messages")
                    (has (status? 302))
                    (follow-redirect)
                    (has (some-text? "Authenticate again")))]
      (attack-uri
        state
        "/re-auth"
        {:password "xxx"}
        standard-attack))))

(deftest attack-re-auth-ajax
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [state (-> *s*
                    (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
                    (visit "/user/messages")
                    (has (status? 200))
                    (visit "/debug/set-session" :params {:auth-re-auth true})
                    (visit "/user/messages")
                    (has (status? 302))
                    (follow-redirect)
                    (has (some-text? "Authenticate again")))]
      (attack-uri
        state
        "/re-auth-ajax"
        {:password "xxx"}
        standard-attack))))

(deftest attack-login-double-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (attack-uri
          "/login"
          {:username "536975" :password "xxx"}
          (repeat 1 [0 422]))
        (visit "/login" :request-method :post :params {:username "536975" :password "536975"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (attack-uri
          "/double-auth"
          {:code "xxx"}
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
        (advance-time-s! (mw/re-auth-timeout))
        (visit "/user/messages")
        (has (status? 302))
        (attack-uri
          "/re-auth-ajax"
          {:password "xxx"}
          (concat
            (repeat 10 [0 422])
            (repeat 10 [0 429])))
        (advance-time-s! a-d/const-ip-block-delay)
        (visit "/re-auth-ajax" :request-method :post :params {:password "536975"})
        (has (status? 200)))))

(deftest attack-parallel
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (attack-uri
          "/login"
          {:username "536975" :password "xxx"}
          [[0 422]])
        (visit "/login" :request-method :post :params {:username "536975" :password "536975"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (attack-uri
          "/double-auth"
          {:code "xxx"}
          [[70 422]
           [70 422]]))
    (-> *s*
        (attack-uri
          "/login"
          {:username "xxx" :password "xxx"}
          (concat
            (repeat
              (- a-d/const-fails-until-ip-block 3)
              [0 422])
            [[0 429]
             [a-d/const-ip-block-delay 422]])))))

(deftest attack-parallel-different-ips
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (attack-uri
          "/login"
          {:username "536975" :password "xxx"}
          [[0 422]])
        (visit "/login" :request-method :post :params {:username "536975" :password "536975"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (attack-uri
          "/double-auth"
          {:code "xxx"}
          [[70 422]
           [70 422]]))
    (-> *s*
        (attack-uri
          "/login"
          {:username "xxx" :password "xxx"}
          standard-attack
          "192.168.0.1"))))


(deftest attack-registration
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
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
          {:captcha "xxx"}
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
      {:username "xxx" :password "xxx"}
      (repeat (/ a-d/const-fails-until-global-block 10) [1 422])
      (str ip-address)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"} :remote-addr "hejsan")
      (has (status? 429)))
  (advance-time-s! a-d/const-global-block-delay)
  (-> *s*
      (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"} :remote-addr "hoppsan")
      (has (status? 422)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"} :remote-addr "tjosan")
      (has (status? 429)))
  (-> *s*
      (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"} :remote-addr "METALLICA")
      (has (status? 429)))
  (advance-time-s! a-d/const-global-block-delay)
  (-> *s*
      (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"} :remote-addr "SLAYER")
      (has (status? 422)))
  (advance-time-s! a-d/const-attack-interval)
  (dotimes [ip-address 5]
    (attack-uri
      *s*
      "/login"
      {:username "xxx" :password "xxx"}
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