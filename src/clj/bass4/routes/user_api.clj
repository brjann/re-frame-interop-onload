(ns bass4.routes.user-api
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.user :as user-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.route-rules :as route-rules]))


(defn api-response-mw
  "Returns only status code for error responses
  (i.e., strips body)"
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/api/*"]
    (fn [handler]
      (fn [request]
        (let [response (handler request)]
          (if (<= 400 (:status response))
            {:status (:status response)}
            response))))))


; -----------------------
;          ROUTES
; -----------------------

(defroutes api-routes
  (context "/user/api" [:as {{:keys [user]} :db}]
    (GET "/privacy-notice" []
      (user-response/privacy-notice-bare user))))
