(ns bass4.password.routes
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [bass4.password.lost-responses :as lpw-res]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.password.services :as lpw-service]
            [bass4.route-rules :as route-rules]))

(def rules
  [{:pattern #"^/lost-password/request-email.*"
    :handler (fn [_] (= :request-email (lpw-service/lost-password-method)))}
   {:pattern #"^/lost-password/report.*"
    :handler (fn [_] (= :report (lpw-service/lost-password-method)))}])


(def lpw-rules
  [{:uri   "/lost-password/request-email*"
    :rules [[(fn [_ _] (= :request-email (lpw-service/lost-password-method))) :ok 403]]}
   {:uri   "/lost-password/report*"
    :rules [[(fn [_ _] (= :report (lpw-service/lost-password-method))) :ok 403]]}])

(defn lpw-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/lost-password/*"]
    (route-rules/wrap-rules lpw-rules)))

(defn- re-router
  []
  (let [method (lpw-service/lost-password-method)
        route  (case method
                 :request-email "/lost-password/request-email"
                 :report "/lost-password/report")]
    (http-response/found route)))

(defroutes lost-password-routes
  (GET "/lpw-uid/:uid" [uid]
    (http-response/found (str "/lost-password/request-email/uid/" uid)))
  (context "/lost-password" []
    (GET "/" []
      (re-router))
    (context "/request-email" []
      (GET "/" []
        (lpw-res/request-page))
      (POST "/" [username :as request]
        (lpw-res/handle-request username request))
      (GET "/sent" []
        (lpw-res/request-sent))
      (GET "/uid/:uid" [uid]
        (lpw-res/handle-request-uid uid))
      (GET "/received" []
        (lpw-res/request-received))
      (GET "/not-found" []
        (lpw-res/request-not-found)))
    (context "/report" []
      (GET "/" []
        (lpw-res/report-page))
      (POST "/" [username]
        (lpw-res/handle-report username))
      (GET "/received" []
        (lpw-res/report-received)))))