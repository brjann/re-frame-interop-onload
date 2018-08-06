(ns bass4.test.reqs-lost-password
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return log-body *s* fix-time advance-time-s! disable-attack-detector pass-by]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.attack-detector :as a-d]
            [clojure.string :as s]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.services.lost-password :as lpw-service]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (jdbc/execute! db/*db* "DELETE FROM c_flag WHERE ParentId = 605191")
    (fix-time
      (f))
    (jdbc/execute! db/*db* "DELETE FROM c_flag WHERE ParentId = 605191")))

(def uid (atom nil))

(defn has-flag?
  []
  (let [res (jdbc/query db/*db* "SELECT count(*) AS `count` FROM c_flag WHERE ParentId = 605191")]
    (not (zero? (val (ffirst res))))))

(defn set-uid!
  [s]
  (let [headers (get-in s [:response :headers])
        mail    (get headers "X-Debug-Headers")
        url     (second (re-matches #"[\s\S]*?(http.*)[\s\S]*" mail))
        uid*    (subs url (inc (s/last-index-of url "/")))]
    (when-not uid*
      (throw (Exception. "No UID found in " headers)))
    (swap! uid (constantly uid*)))
  s)

(deftest lost-password-router
  (with-redefs [lpw-service/lost-password-method (constantly :report)]
    (-> *s*
        (visit "/lost-password/request-email")
        (has (status? 403))))
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/report")
        (has (status? 403)))))

(deftest lost-password-request-email-flow
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password")
        (has (status? 302))
        (follow-redirect)
        (has (status? 200))
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost-password"})
        (debug-headers-text? "can only be used once")
        (set-uid!)
        (follow-redirect)
        (has (some-text? "sent"))
        ;; Shortcut
        (visit (str "/lpw-uid/" @uid))
        (has (status? 302))
        ;; Redirected from shortcut
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "received")))
    (is (has-flag?))))

(deftest lost-password-request-email-email
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost@password.com"})
        (set-uid!)
        (follow-redirect)
        (visit (str "/lost-password/request-email/uid/" @uid))
        (follow-redirect)
        (has (some-text? "received")))
    (is (has-flag?))))

(deftest lost-password-request-email-wrong-uid
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost-password"})
        (set-uid!)
        (visit (str "/lost-password/request-email/uid/xxx"))
        (follow-redirect)
        (has (some-text? "Invalid")))
    (is (false? (has-flag?)))))

(deftest lost-password-request-email-old-uid
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost-password"})
        (set-uid!)
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost-password"})
        (visit (str "/lost-password/request-email/uid/" @uid))
        (follow-redirect)
        (has (some-text? "Invalid")))
    (is (false? (has-flag?)))))

(deftest lost-password-request-email-uid-expired
  (with-redefs [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/request-email" :request-method :post :params {:username "lost-password"})
        (set-uid!)
        (advance-time-s! (inc lpw-service/uid-time-limit))
        (visit (str "/lost-password/request-email/uid/" @uid))
        (follow-redirect)
        (has (some-text? "Invalid")))
    (is (false? (has-flag?)))))

(deftest lost-password-report-flow
  (with-redefs [lpw-service/lost-password-method (constantly :report)]
    (-> *s*
        (visit "/lost-password")
        (has (status? 302))
        (follow-redirect)
        (has (status? 200))
        (visit "/lost-password/report" :request-method :post :params {:username "lost-password"})
        (follow-redirect)
        (has (some-text? "received")))
    (is (has-flag?))))

;; TODO: This test
(comment (deftest lost-password-report-flow-wrong-user
           (with-redefs [lpw-service/lost-password-method (constantly :report)]
             (-> *s*
                 (visit "/lost-password")
                 (has (status? 302))
                 (follow-redirect)
                 (has (status? 200))
                 (visit "/lost-password/report" :request-method :post :params {:username "lost-password"})
                 (follow-redirect)
                 (has (some-text? "received")))
             (is (has-flag?)))))