(ns bass4.routes.lost-password
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [bass4.responses.lost-password :as lpw-res]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.lost-password :as lpw-service]))

(def rules
  [{:pattern  #"^/lost-password/request-email.*"
    :handler  (fn [_] (= :request-email (lpw-service/lost-password-method)))
    :on-error (constantly (layout/text-response "No way!"))}
   {:pattern  #"^/lost-password/report.*"
    :handler  (fn [_] (= :report (lpw-service/lost-password-method)))
    :on-error (constantly (layout/text-response "No way!"))}])

(defroutes lost-password-routes
  (GET "/lpw-uid/:uid" [uid]
    (http-response/found (str "/lost-password/request-email/uid/" uid)))
  (context "/lost-password" []
    (GET "/" []
      (http-response/found "/lost-password/request-email"))
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
      (POST "/" [username :as request]
        (lpw-res/handle-report username request)))))