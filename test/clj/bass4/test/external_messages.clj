(ns ^:eftest/synchronized
  bass4.test.external-messages
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer [test-fixtures messages-are? *s*]]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [chan dropping-buffer <!! poll! put!]]
            [bass4.external-messages.email-sender :as email]
            [clojure.string :as str]
            [bass4.external-messages.sms-sender :as sms]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.now :as now]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.external-messages.sms-queue :as sms-queue]
            [clj-time.core :as t]
            [bass4.external-messages.sms-status :as sms-status]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  (fn [f]
    (jdbc/execute! db/*db* "TRUNCATE TABLE external_message_email")
    (jdbc/execute! db/*db* "TRUNCATE TABLE external_message_sms")
    (f)))

(defn get-message-count
  [type]
  (-> (jdbc/query db/*db* (str "SELECT sum(count) FROM external_message_count WHERE type='" type "'"))
      first
      vals
      first))

;; -------------
;;  SEND ASYNC
;; -------------

(deftest send-email-out-async
  (let [email-count (get-message-count "email")
        res-a       (atom nil)
        out-str     (with-out-str (binding [email/*email-reroute* :out]
                                    (reset! res-a (<!! (email/async-email! db/*db* "brjann@gmail.com" "XXX" "YYY")))))]
    (messages-are? [[:email "YYY"]] [(:message @res-a)])
    (is (str/includes? out-str "email"))
    (is (str/includes? out-str "brjann@gmail.com"))
    (is (str/includes? out-str "XXX"))
    (is (str/includes? out-str "YYY"))
    (is (== (inc email-count) (get-message-count "email")))))

(deftest send-sms-out-async
  (let [sms-count (get-message-count "sms")
        res-a     (atom nil)
        out-str   (with-out-str (binding [sms/*sms-reroute* :out]
                                  (reset! res-a (<!! (sms/async-sms! db/*db* "666" "ZZZ")))))]
    (messages-are? [[:sms "ZZZ"]] [(:message @res-a)])
    (is (str/includes? out-str "SMS"))
    (is (str/includes? out-str "666"))
    (is (str/includes? out-str "ZZZ"))
    (is (== (inc sms-count) (get-message-count "sms")))))

;; -------------
;;   SEND NOW
;; -------------

(deftest send-email-out-now
  (let [email-count (get-message-count "email")
        res         (with-out-str (binding [email/*email-reroute* :out]
                                    (email/send-email-now! db/*db* "brjann@gmail.com" "XXX" "YYY")))]
    (is (str/includes? res "email"))
    (is (str/includes? res "brjann@gmail.com"))
    (is (str/includes? res "XXX"))
    (is (str/includes? res "YYY"))
    (is (== (inc email-count) (get-message-count "email")))))

(deftest send-sms-out-now
  (let [sms-count (get-message-count "sms")
        res       (with-out-str (binding [sms/*sms-reroute* :out]
                                  (sms/send-sms-now! db/*db* "666" "ZZZ")))]
    (is (str/includes? res "SMS"))
    (is (str/includes? res "666"))
    (is (str/includes? res "ZZZ"))
    (is (== (inc sms-count) (get-message-count "sms")))))

;; -------------
;;  EMAIL QUEUE
;; -------------

(deftest email-queue
  (let [c (chan 3)]
    (binding [email/*email-reroute* c]
      (email-queue/add! db/*db* (now/now) [{:user-id 1 :to "mail1@example.com" :subject "s1" :message "m1"}
                                           {:user-id 2 :to "mail2@example.com" :subject "s2" :message "m2"}
                                           {:user-id 3 :to "mail3@example.com" :subject "s3" :message "m3"}])
      (email-queue/send! db/*db* {:name :test} (now/now))
      (is (= #{["mail1@example.com" "s1" "m1"]
               ["mail2@example.com" "s2" "m2"]
               ["mail3@example.com" "s3" "m3"]}
             (into #{} (repeatedly 3 #(butlast (butlast (into [] (poll! c))))))))
      (email-queue/send! db/*db* {:name :test} (now/now))
      (is (nil? (poll! c))))))

(deftest email-fail
  (let [c (chan)]
    (binding [email/*email-reroute*   :exception
              email-queue/max-fails   5
              email/send-error-email! #(put! c %2)]
      (email-queue/add! db/*db* (now/now) [{:user-id 1 :to "mail1@example.com" :subject "s1" :message "m1"}
                                           {:user-id 2 :to "mail2@example.com" :subject "s2" :message "m2"}
                                           {:user-id 3 :to "mail3@example.com" :subject "s3" :message "m3"}])
      (doseq [n (range 5)]
        (let [res (email-queue/send! db/*db* {:name :test} (now/now))]
          (is (= 3 (count (:exception res))))
          (is (= 0 (:success res)))
          (if (= 4 n)
            (is (string? (poll! c)))
            (is (nil? (poll! c)))))
        #_(is (= {:exception nil :fail 3 :success 0} res)))
      (is (= {:exception nil :success 0}
             (email-queue/send! db/*db* {:name :test} (now/now)))))))

;; -------------
;;   SMS QUEUE
;; -------------

(deftest sms-queue
  (let [c          (chan 3)
        sender     (sms/get-sender db/*db*)
        status-url (sms-status/status-url db/*db*)]
    (binding [sms/*sms-reroute* c]
      (sms-queue/add! db/*db* (now/now) [{:user-id 1 :to "1" :message "m1"}
                                         {:user-id 2 :to "2" :message "m2"}
                                         {:user-id 3 :to "3" :message "m3"}])
      (sms-queue/send! db/*db* {:name :test} (now/now))
      (is (= #{["1" "m1" sender status-url]
               ["2" "m2" sender status-url]
               ["3" "m3" sender status-url]}
             (into #{} (repeatedly 3 (fn [] (let [x (->> (poll! c)
                                                         (into []))]
                                              (conj (subvec x 0 3)
                                                    (:status-url (get x 3)))))))))
      (sms-queue/send! db/*db* {:name :test} (now/now))
      (is (nil? (poll! c))))))

(deftest sms-fail
  (let [c (chan)]
    (binding [sms/*sms-reroute*       :exception
              sms-queue/max-fails     5
              email/send-error-email! #(put! c %2)]
      (sms-queue/add! db/*db* (now/now) [{:user-id 1 :to "1" :message "m1"}
                                         {:user-id 2 :to "2" :message "m2"}
                                         {:user-id 3 :to "3" :message "m3"}])
      (doseq [n (range 5)]
        (let [res (sms-queue/send! db/*db* {:name :test} (now/now))]
          (is (= 3 (count (:exception res))))
          (is (= 0 (:success res)))
          (if (= 4 n)
            (is (string? (poll! c)))
            (is (nil? (poll! c))))))
      (is (= {:exception nil :success 0}
             (sms-queue/send! db/*db* {:name :test} (now/now)))))))

;; -------------
;;   SMS STATUS
;; -------------

(deftest sms-status
  (-> *s*
      (visit "/sms-status" :request-method :post :params {:ref 666 :state "delivrd" :datetime "2017-01-01 10:00:00"})
      (has (status? 200))
      (visit "/sms-status" :request-method :post :params {})
      (has (status? 400))
      (visit "/sms-status" :request-method :post :params {:ref 666 :state "delivrd" :datetime "2017-01-0110:00:00"})
      (has (status? 400))))