(ns bass4.test.reqs-attack
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? debug-headers-not-text? log-return]]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.core :as mw]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.services.registration :as reg-service]
            [bass4.responses.registration :as reg-response]
            [bass4.passwords :as passwords]
            [bass4.services.attack-detector :as a-d]))


(use-fixtures
  :once
  test-fixtures)

(def now (atom nil))

(defn advance-time!
  [state secs]
  (swap! now (constantly (t/plus (t/now) (t/seconds secs))))
  state)

(use-fixtures
  :each
  (fn [f]
    (db/clear-failed-logins!)
    (swap! a-d/blocked-ips (constantly {}))
    (swap! a-d/blocked-last-request (constantly {}))
    (swap! now (constantly (t/now)))
    (with-redefs
      [t/now (fn [] @now)]
      (f))))

(defn attack-uri
  ([state uri post attacks]
   (attack-uri state uri post attacks "localhost"))
  ([state uri post attacks from-ip-address]
   (loop [current-state state attacks attacks]
     (if (seq attacks)
       (let [[wait status] (first attacks)]
         (-> current-state
             (advance-time! wait)
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
    (repeat a-d/const-fails-until-ip-block [0 422])
    (repeat 3 [0 429])
    [[(dec a-d/const-block-delay) 429]
     [1 422]
     [0 429]
     [a-d/const-attack-interval 422]]
    (repeat (dec a-d/const-fails-until-ip-block) [0 422])
    [[0 429]]))


(deftest attack-login
  (-> (session (app))
      (attack-uri
        "/login"
        {:username "xxx" :password "xxx"}
        standard-attack)
      (visit "/login" :request-method :post :params {:username 536975 :password 536975})
      (has (status? 429))
      (advance-time! a-d/const-block-delay)
      (visit "/login" :request-method :post :params {:username 536975 :password 536975})
      (has (status? 302))))


(deftest attack-double-auth
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [state (-> (session (app))
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
    (let [state (-> (session (app))
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
    (let [state (-> (session (app))
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
    (-> (session (app))
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
            (repeat (dec a-d/const-block-delay) [0 422])
            [[0 429]
             [0 429]
             [0 429]
             [(dec a-d/const-block-delay) 429]
             [1 422]]))
        (advance-time! a-d/const-block-delay)
        (visit "/double-auth" :request-method :post :params {:code "666777"})
        (has (status? 302))
        (follow-redirect)
        (advance-time! (mw/re-auth-timeout))
        (visit "/user/messages")
        (has (status? 302))
        (attack-uri
          "/re-auth-ajax"
          {:password "xxx"}
          (concat
            (repeat 10 [0 422])
            (repeat 10 [0 429])))
        (advance-time! a-d/const-block-delay)
        (visit "/re-auth-ajax" :request-method :post :params {:password "536975"})
        (has (status? 200)))))

(deftest attack-parallel
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [s1 (session (app))
          s2 (session (app))]
      (-> s1
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
      (-> s2
          (attack-uri
            "/login"
            {:username "xxx" :password "xxx"}
            (concat
              (repeat
                (- a-d/const-fails-until-ip-block 3)
                [0 422])
              [[0 429]
               [a-d/const-block-delay 422]]))))))

(deftest attack-parallel-different-ips
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (let [s1 (session (app))
          s2 (session (app))]
      (-> s1
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
      (-> s2
          (attack-uri
            "/login"
            {:username "xxx" :password "xxx"}
            standard-attack
            "192.168.0.1")))))


(deftest attack-registration
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:fields                 #{:first-name :last-name}
                                                             :group                  570281 ;;No assessments in this group
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :auto-id-prefix         "xxx-"
                                                             :auto-id-length         3
                                                             :auto-id?               true})]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
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
        (advance-time! 9)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610" :request-method :post :params {:first-name "Lasse" :last-name "Basse"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "we promise")))))

#_(log/debug (-> (session (app))
                 (visit "/debug/headers" :remote-addr "1000")))
