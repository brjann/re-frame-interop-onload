(ns bass4.routes.debug
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.pprint]
            [ring.util.codec :as codec]
            [bass4.external-messages.email-sender :as mail]
            [clj-http.client :as http]
            [bass4.http-utils :as h-utils]
            [bass4.i18n :as i18n]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.responses.e-auth :as e-auth-response]
            [ring.util.http-response :as http-response]
            [clj-time.coerce :as tc]
            [bass4.time :as b-time]
            [clojure.string :as s]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.client-config :as client-config]))

(defn check-pending-http
  [participant-id request]
  (let [host-address (h-utils/get-host-address request)]
    (-> (str host-address "/ext-login/check-pending/" participant-id)
        (http/get)
        (:body)
        (str "&returnURL=" (codec/url-encode (str host-address "/debug/session")))
        (layout/text-response))))

(def debug-routes
  (context "/debug" [:as request]
    (if (or (client-config/debug-mode?))
      (routes
        (GET "/nothing" [] (layout/text-response "nothing"))
        (GET "/timezone" [:as req] (layout/print-var-response (client-config/db-setting [:timezone])))
        (GET "/session" [:as req] (layout/print-var-response (:session req)))
        (GET "/error" [:as req] (do
                                  (request-logger/record-error! "An evil error message")
                                  (str "Ten divided by zero: " (/ 10 0))))
        (ANY "/request" [:as req] (layout/print-var-response req))
        (GET "/test" [:as req]
          (layout/render "test.html"))
        (GET "/sleep1" []
          (do (Thread/sleep 10000)
              (layout/text-response "I slept for 20 secs")))
        (GET "/sleep2" []
          (do (Thread/sleep 20000)
              (layout/text-response "I slept for 20 secs")))
        (GET "/ie-test/:xxx/:yyy" [:as req]
          (layout/render "ie-error-sleep-diary.html"
                         {:url :url}))
        (POST "/ie-test/:xxx/:yyy" [:as req]
          (layout/text-response "You posted data"))
        (GET "/ie-test/:xxx/:yyy/" [:as req]
          (layout/render "ie-error-sleep-diary.html"
                         {:url :url}))
        (POST "/ie-test/:xxx/:yyy/" [:as req]
          (layout/text-response "You posted data"))
        (GET "/env" [:as req] (layout/print-var-response env))
        (GET "/timeout" [:as request]
          (-> (http-response/found "/re-auth?return-url=/user")
              (assoc :session
                     (merge (:session request)
                            {:auth-re-auth? true}))))
        (GET "/set-session" [& params :as request]
          (-> (http-response/found "/debug/session")
              (assoc :session
                     (merge (:session request)
                            (map-map #(if-let [x (str->int %)] x %) params)))))
        (GET "/delete-session" [& params :as request]
          (-> (http-response/found "/debug/session")
              (assoc :session {})))
        (POST "/403" [& params :as request]
          (http-response/forbidden "Sorry!"))
        (GET "/403" [& params :as request]
          (http-response/forbidden "Sorry!"))
        (GET "/404" [& params :as request]
          (http-response/not-found "Not found"))
        (GET "/404!" [& params :as request]
          (http-response/not-found!))

        ;; sms and email test functions
        (GET "/mail-debug" [& params :as request]
          (let [email (env :error-email "brjann@gmail.com")]
            (layout/text-response (mail/send-email*! email "Test email" "This is the test message" nil true))))
        (GET "/mail" [& params :as request]
          (let [email (or (:mail params) (env :error-email "brjann@gmail.com"))]
            (layout/text-response (mail/send-email! email "Test email" "This is the test message"))))
        (GET "/sms" [& params :as request]
          (let [sms-number (or (:sms params) (env :error-sms "+46707176562"))]
            (layout/text-response (sms/async-sms! db/*db* sms-number "This is the test sms"))))
        (POST "/found" []
          (-> (http-response/found "/login")))
        (POST "/params" [& params]
          (layout/print-var-response params))
        (GET "/json" [& params]
          (-> (http-response/ok (json/write-str params))
              (http-response/content-type "application/json")))
        (GET "/encode" []
          (-> (http-response/found (str "/debug/decode?url=" (codec/url-encode "/debug/encode-decode?arg1=val1&arg2=val2&arg3=path%2fto%2fresource")))))
        (GET "/decode" [& params]
          (-> (http-response/found (:url params))))
        (GET "/ip" []
          (layout/text-response
            (str "x-ip " (get-in request [:headers "x-forwarded-for"]) "\n"
                 "ip " (:remote-addr request) "\n"
                 "selected ip " (h-utils/get-client-ip request))))
        (GET "/encode-decode" [& params]
          (layout/print-var-response params))
        (GET "/exception" []
          (throw (Exception. "Your exception as requested.")))
        (GET "/ext-login/:participant-id" [participant-id]
          (check-pending-http participant-id request))
        (GET "/i18n-merge/:lang" [lang]
          (layout/text-response (i18n/merge-i18n lang)))
        (GET "/server-time-zone" []
          (layout/text-response (:timezone (db/get-time-zone))))
        (GET "/markdown-list" [& params]
          (layout/render "render.html"
                         {:text      "1. Foo\n2. Bar\n3. Baz"
                          :markdown? true}))
        (GET "/bankid-test" []
          (layout/render "bankid-test.html"))
        (POST "/bankid-launch" [& params :as request]
          (e-auth-response/launch-bankid-test
            request
            (:personnummer params)
            (:redirect-success params)
            (:redirect-fail params)))
        (GET "/bankid-success" [:as request]
          (e-auth-response/bankid-success (:session request)))
        (GET "/resource" []
          (let [url  (io/resource "public/js/form-ajax.js")
                data (http-response/resource-data url)]
            (layout/text-response (b-time/to-unix (tc/from-date (:last-modified data)))))))
      (routes
        (ANY "*" [] "Not in debug mode")))))