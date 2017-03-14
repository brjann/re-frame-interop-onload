(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.services.user :as user]))

#_(defroutes user-routes
  (context "/user" []
    #_(GET "/" req (dashboard-page req))
    #_(GET "/profile" [errors :as req] (profile-page req errors))
    #_(GET "/modules" req (modules-page req))
    #_(GET "/worksheets" [worksheet-id :as req] (worksheets-page worksheet-id req))
    #_(POST "/worksheets" [& params :as req] (handle-worksheet-submit params req))
    (GET "/messages" [errors :as req] (messages-response/messages-page req errors))
    (POST "/messages" [& params :as req] (messages-response/save-message params req))
    (POST "/message-save-draft" [& params :as req] (messages-response/save-draft params req))
    #_(GET "/charts" req (charts-page req))))

(def user-routes
    (context "/user" [:as req]
      (if-let [user (user/get-user (:identity req))]
        (routes
          #_(GET "/" req (dashboard-page req))
          #_(GET "/profile" [errors :as req] (profile-page req errors))
          #_(GET "/modules" req (modules-page req))
          #_(GET "/worksheets" [worksheet-id :as req] (worksheets-page worksheet-id req))
          #_(POST "/worksheets" [& params :as req] (handle-worksheet-submit params req))
          (GET "/messages" [errors] (messages-response/messages-page user errors))
          (POST "/messages" [& params] (messages-response/save-message user params))
          (POST "/message-save-draft" [& params] (messages-response/save-draft user params))
          #_(GET "/charts" req (charts-page req)))
        (routes
          (ANY "*" [] "no such user")))))