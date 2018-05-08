(ns bass4.bankid
  (:require [clojure.core.async
             :refer [>! <! go chan timeout]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe]]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]))

(defn print-status
  [uid s]
  (println (str (subs (str uid) 0 4) " " s)))

#_(def collect-responses
    {:pending {:outstandingTransaction :RFA13}})

(def auth-params
  {:keystore         "/Users/brjljo/Dropbox/Plattform/bass4/BankID/keystore_with_private.jks"
   :keystore-pass    "changeit"
   :trust-store      "/Users/brjljo/Dropbox/Plattform/bass4/BankID/truststore.jks"
   :trust-store-pass "changeit"
   :content-type     :json})

(defn bankid-request
  [endpoint form-params]
  (try (let [response (http/post (str "https://appapi2.test.bankid.com/rp/v5/" endpoint)
                                 (merge auth-params {:form-params form-params}))]
         (when (= 200 (:status response))
           (-> response
               :body
               json-safe
               walk/keywordize-keys)))
       (catch Exception _ nil)))

(defn bankid-auth
  []
  (bankid-request "auth"
                  {"personalNumber" "197807129379"
                   "endUserIp"      "81.232.173.180"}))

(defn bankid-collect
  [order-ref]
  (bankid-request "collect" {"orderRef" order-ref}))

(defn start-bankid-session
  [uid]
  (print-status uid "Started request")
  (let [start-chan (chan)]
    (go
      (>! start-chan (or (bankid-auth) {})))
    start-chan))

(defn collect-bankid
  [uid order-ref]
  (print-status uid "Collecting")
  (let [collect-chan (chan)]
    (go
      (>! collect-chan (or (bankid-collect order-ref) {})))
    collect-chan))

(def statuses (atom {}))

(defn get-status
  [uid]
  (get-in @statuses [uid :status]))

(defn get-status-info
  [uid]
  (get @statuses uid))

(defn set-status!
  ([uid status] (set-status! uid status {}))
  ([uid status info]
   (print-status uid (str "status of uid =" status))
   (swap! statuses #(assoc % uid (merge {:status status} info)))))

(defn launch-bankid
  []
  (let [uid (java.util.UUID/randomUUID)]
    (set-status! uid :initializing)
    (go (let [start-chan (start-bankid-session uid)
              response   (<! start-chan)]
          (if (seq response)
            (let [order-ref        (:orderRef response)
                  auto-start-token (:autoStartToken response)]
              (print-status uid (str "BankID session started with order-ref" order-ref))
              (set-status! uid :started)
              (while (contains? #{:started :pending} (get-status uid))
                ;; Poll once every second
                (<! (timeout 1000))
                (let [collect-chan (collect-bankid uid order-ref)
                      response     (<! collect-chan)]
                  (if (seq response)
                    (let [status    (keyword (:status response))
                          hint-code (keyword (:hintCode response))]
                      (set-status! uid status))
                    (set-status! uid :failed)))))
            (print-status uid "Launch failed")))
        (print-status uid (str "Request completed with status " (get @statuses uid))))
    uid))