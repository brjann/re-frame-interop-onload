(ns bass4.test.external-messages
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer [test-fixtures messages-are?]]
            [clojure.core.async :refer [chan dropping-buffer <!!]]
            [bass4.email :as email]
            [clojure.string :as str]
            [bass4.sms-sender :as sms]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]))

(use-fixtures
  :once
  test-fixtures)

(defn get-message-count
  [type]
  (-> (jdbc/query db/*db* (str "SELECT sum(count) FROM external_message_count WHERE type='" type "'"))
      first
      vals
      first))

(deftest send-email-out-queue
  (let [email-count (get-message-count "email")
        res-a       (atom nil)
        out-str     (with-out-str (binding [email/*email-reroute* :out]
                                    (reset! res-a (<!! (email/queue-email! "brjann@gmail.com" "XXX" "YYY")))))]
    (messages-are? [[:email "YYY"]] [(:message @res-a)])
    (is (str/includes? out-str "email"))
    (is (str/includes? out-str "brjann@gmail.com"))
    (is (str/includes? out-str "XXX"))
    (is (str/includes? out-str "YYY"))
    (is (== (inc email-count) (get-message-count "email")))))

(deftest send-sms-out-queue
  (let [sms-count (get-message-count "sms")
        res-a     (atom nil)
        out-str   (with-out-str (binding [sms/*sms-reroute* :out]
                                  (reset! res-a (<!! (sms/queue-sms! "666" "ZZZ")))))]
    (messages-are? [[:sms "ZZZ"]] [(:message @res-a)])
    (is (str/includes? out-str "SMS"))
    (is (str/includes? out-str "666"))
    (is (str/includes? out-str "ZZZ"))
    (is (== (inc sms-count) (get-message-count "sms")))))

(deftest send-email-out-now
  (let [email-count (get-message-count "email")
        res         (with-out-str (binding [email/*email-reroute* :out]
                                    (email/send-email-now! "brjann@gmail.com" "XXX" "YYY")))]
    (is (str/includes? res "email"))
    (is (str/includes? res "brjann@gmail.com"))
    (is (str/includes? res "XXX"))
    (is (str/includes? res "YYY"))
    (is (== (inc email-count) (get-message-count "email")))))

(deftest send-sms-out-now
  (let [sms-count (get-message-count "sms")
        res       (with-out-str (binding [sms/*sms-reroute* :out]
                                  (sms/send-sms-now! "666" "ZZZ")))]
    (is (str/includes? res "SMS"))
    (is (str/includes? res "666"))
    (is (str/includes? res "ZZZ"))
    (is (== (inc sms-count) (get-message-count "sms")))))