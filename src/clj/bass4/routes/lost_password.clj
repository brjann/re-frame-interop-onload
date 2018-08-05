(ns bass4.routes.lost-password
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [bass4.responses.lost-password :as lpw-res]
            [ring.util.http-response :as http-response]))

(defroutes lost-password-routes
  (GET "/lpw-uid/:uid" [uid]
    (http-response/found (str "/lost-password/request/uid/" uid)))
  (context "/lost-password" []
    (GET "/" []
      (lpw-res/lost-password-page))
    (POST "/" [username :as request]
      (lpw-res/handle-request username request))
    (GET "/request/sent" []
      (lpw-res/request-sent))
    (GET "/request/uid/:uid" [uid]
      (lpw-res/handle-request-uid uid))
    (GET "/request/received" []
      (lpw-res/request-received))
    (GET "/request/not-found" []
      (lpw-res/request-not-found))))