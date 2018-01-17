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
            [bass4.captcha :as captcha]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.services.bass :as bass]
            [mount.core :as mount]
            [clojure.pprint]
            [bass4.request-state :as request-state]
            [ring.util.codec :as codec]))

(defn states-page
  []
  (layout/render "states.html" {:states (mapv #(subs % 2) (mount/find-all-states))}))

(defn reset-state
  [state-name]
  (let [states     (mount/find-all-states)
        state-name (str "#'" state-name)]
    (when (some #{state-name} states)
      (mount.core/stop state-name)
      (mount.core/start state-name))
    (http-response/found "/debug/states")))

(def debug-routes
  (context "/debug" [:as request]
    (if (or (env :debug-mode) (env :dev))
      (routes
        (GET "/timezone" [:as req] (layout/text-response (locals/time-zone)))
        (GET "/session" [:as req] (layout/text-response (:session req)))
        (GET "/error" [:as req] (do
                                  (request-state/record-error! "An evil error message")
                                  (str "Ten divided by zero: " (/ 10 0))))
        (GET "/request" [:as req] (layout/text-response req))
        (GET "/test" [:as req]
          (layout/render "test.html"
                         {:url :url}))
        (GET "/env" [:as req] (layout/text-response env))
        (GET "/timeout" [:as request]
          (-> (http-response/found "/re-auth")
              (assoc :session
                     (merge (:session request)
                            {:auth-re-auth true}))))
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
        (GET "/403" [& params :as request]
          (layout/error-403-page (get-in request [:session :identity])))
        (GET "/404!" [& params :as request]
          (http-response/not-found!))
        (POST "/found" []
          (-> (http-response/found "/login")))
        (POST "/params" [& params]
          (layout/text-response params))
        (GET "/encode" []
          (-> (http-response/found (str "/debug/decode?url=" (codec/url-encode "/debug/encode-decode?arg1=val1&arg2=val2&arg3=path%2fto%2fresource")))))
        (GET "/decode" [& params]
          (-> (http-response/found (:url params))))
        (GET "/encode-decode" [& params]
          (layout/text-response params))
        (GET "/exception" []
          (throw (Exception. "Your exception as requested.")))
        (GET "/states" []
          (states-page))
        (POST "/states" [& params]
          (reset-state (:state-name params))))
      (routes
        (ANY "*" [] "Not in debug mode")))))