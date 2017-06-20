(ns bass4.routes.debug
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.services.bass :as bass]
            [clojure.pprint]
            [bass4.request-state :as request-state]))


(defn text-response
  [var]
  (response/content-type (response/response (with-out-str (clojure.pprint/pprint var))) "text/plain"))

(def debug-routes
  (context "/debug" [:as request]
    (if (or (env :debug-mode) (env :dev))
      (routes
        (GET "/timezone" [:as req] (text-response (locals/time-zone)))
        (GET "/session" [:as req] (text-response (:session req)))
        (GET "/error" [:as req] (do
                                  (request-state/record-error! "An evil error message")
                                  (str "Ten divided by zero: " (/ 10 0))))
        (GET "/request" [:as req] (text-response req))
        (GET "/test" [:as req] (text-response (:server-name req)))
        (GET "/env" [:as req] (text-response env))
        (GET "/timeout" [:as request]
          (-> (http-response/found "/re-auth")
              (assoc :session
                     (merge (:session request)
                            {:auth-timeout true}))))
        (GET "/set-session" [& params :as request]
          (-> (http-response/found "/debug/session")
              (assoc :session
                     (merge (:session request)
                            (map-map #(if-let [x (str->int %)] x %) params)))))
        (GET "/delete-session" [& params :as request]
          (-> (http-response/found "/debug/session")
              (assoc :session {})))
        (POST "/403" [& params :as request]
          (layout/error-page {:status 403
                              :body "Sorry!"}))
        (POST "/found" []
          (-> (http-response/found "/login"))))
      (routes
        (ANY "*" [] "Not in debug mode")))))