(ns bass4.test.reqs-attack-sync
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.test.reqs-attack :refer :all]
            [bass4.services.attack-detector :as a-d]))



(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  (fn [f]
    (db/clear-failed-logins!)
    (fix-time
      (binding [a-d/blocked-ips          (atom {})
                a-d/blocked-last-request (atom {})
                a-d/global-block         (atom nil)
                a-d/global-last-request  (atom nil)]
        (f)))
    (db/clear-failed-logins!)))

(deftest attack-login-sync
  (let [user-id (create-user-with-password! {"smsnumber" "00"})]
    (-> *s*
        (attack-uri
          "/login"
          {:username "%€#&()" :password "%€#&()"}
          standard-attack
          "127.0.0.1")
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 429))
        (advance-time-s! a-d/const-ip-block-delay)
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302)))))

