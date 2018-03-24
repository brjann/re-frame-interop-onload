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
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.services.registration :as reg-service]
            [bass4.passwords :as passwords]
            [bass4.services.attack-detector :as a-d]))


(use-fixtures
  :once
  test-fixtures)

(defn clear-blocks!
  []
  (db/clear-failed-logins!)
  (swap! a-d/blocked-ips (constantly {})))

(deftest attack
  (clear-blocks!)
  (let [x (session (app))]
    (dotimes [_ 9]
      (visit x "/login" :request-method :post :params {:username "xxx" :password "xxx"}))
    (-> x
        (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"})
        (debug-headers-not-text? "blocked" "slept")
        (visit "/login" :request-method :post :params {:username "xxx" :password "xxx"})
        (debug-headers-text? "blocked" "slept"))))



