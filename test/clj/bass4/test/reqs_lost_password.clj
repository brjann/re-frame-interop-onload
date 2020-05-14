(ns bass4.test.reqs-lost-password
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.test.core :refer :all]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.password.lost-services :as lpw-service]
            [bass4.external-messages.async :refer [*debug-chan*]]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(def ^:dynamic uid)

(use-fixtures
  :each
  (fn [f]
    (binding [uid          (atom nil)
              *debug-chan* (chan)]
      (fix-time
        (f)))))

(defn has-flag?
  [user-id]
  (let [res (jdbc/query db/*db* ["SELECT count(*) AS `count` FROM c_flag WHERE ParentId = ?" user-id])]
    (not (zero? (val (ffirst res))))))

(defn set-uid!
  [messages]
  (let [mail (:message (first messages))
        url  (second (re-matches #"[\s\S]*?(http.*)[\s\S]*" mail))
        uid* (subs url (inc (str/last-index-of url "/")))]
    (when-not uid*
      (throw (Exception. "No UID found in " messages)))
    (swap! uid (constantly uid*)))
  messages)

(deftest lost-password-router
  (binding [lpw-service/lost-password-method (constantly :report)]
    (-> *s*
        (visit "/lost-password/request-email")
        (has (status? 403))))
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (-> *s*
        (visit "/lost-password/report")
        (has (status? 403)))))

(deftest lost-password-request-email-flow
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password")
          (has (status? 302))
          (follow-redirect)
          (has (status? 200))
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (pass-by (let [messages (poll-message-chan *debug-chan*)]
                     (messages-are?
                       [[:email "can only be used once"]]
                       messages)
                     (set-uid! messages)))
          (follow-redirect)
          (has (some-text? "sent"))
          ;; Shortcut
          (visit (str "/lpw-uid/" @uid))
          (has (status? 302))
          ;; Redirected from shortcut
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "received")))
      (is (has-flag? user-id)))))

(deftest lost-password-request-email-email
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (pass-by (set-uid! (poll-message-chan *debug-chan*)))
          (follow-redirect)
          (visit (str "/lost-password/request-email/uid/" @uid))
          (follow-redirect)
          (has (some-text? "received")))
      (is (has-flag? user-id)))))

(deftest lost-password-request-email-wrong-uid
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (pass-by (set-uid! (poll-message-chan *debug-chan*)))
          (visit (str "/lost-password/request-email/uid/xxx"))
          (follow-redirect)
          (has (some-text? "Invalid")))
      (is (false? (has-flag? user-id))))))

(deftest lost-password-request-email-old-uid
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (pass-by (set-uid! (poll-message-chan *debug-chan*)))
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (visit (str "/lost-password/request-email/uid/" @uid))
          (follow-redirect)
          (has (some-text? "Invalid")))
      (is (false? (has-flag? user-id))))))

(deftest lost-password-request-email-uid-expired
  (binding [lpw-service/lost-password-method (constantly :request-email)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password/request-email" :request-method :post :params {:username user-id})
          (pass-by (set-uid! (poll-message-chan *debug-chan*)))
          (advance-time-s! (inc lpw-service/uid-time-limit))
          (visit (str "/lost-password/request-email/uid/" @uid))
          (follow-redirect)
          (has (some-text? "Invalid")))
      (is (false? (has-flag? user-id))))))

(deftest lost-password-report-flow
  (binding [lpw-service/lost-password-method (constantly :report)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password")
          (has (status? 302))
          (follow-redirect)
          (has (status? 200))
          (visit "/lost-password/report" :request-method :post :params {:username user-id})
          (follow-redirect)
          (has (some-text? "received")))
      (is (has-flag? user-id)))))

(deftest lost-password-report-flow-email
  (binding [lpw-service/lost-password-method (constantly :report)]
    (let [user-id (create-user-with-password! {:email "example@example.com"})]
      (-> *s*
          (visit "/lost-password/report" :request-method :post :params {:username user-id})
          (follow-redirect)
          (has (some-text? "received")))
      (is (has-flag? user-id)))))

(deftest lost-password-report-no-user
  (binding [lpw-service/lost-password-method (constantly :report)]
    (-> *s*
        (visit "/lost-password/report" :request-method :post :params {:username "////////////"})
        (follow-redirect)
        (has (some-text? "received")))))