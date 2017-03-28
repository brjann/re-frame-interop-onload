(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.services.user :as user]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.posts :as posts]
            [ring.util.http-response :as response]
            [ring.util.request :as request]
            [ring.util.codec :as codec]))

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
  (context "/user" [:as request]
    (if-let [user (user/get-user (:identity request))]
      (if (not (get-in request [:session :auth-timeout]))
        (if (auth-response/double-authed? (:session request))
          (routes
            (GET "/" [] "this is the dashboard")
            (GET "/messages" []
              (messages-response/messages-page user))
            (POST "/messages" [& params]
              (messages-response/save-message (:user-id user) (:subject params) (:text params)))
            (POST "/message-save-draft" [& params]
              (messages-response/save-draft (:user-id user) (:subject params) (:text params)))
            #_(GET "/" req (dashboard-page req))
            #_(GET "/profile" [errors :as req] (profile-page req errors))
            #_(GET "/modules" req (modules-page req))
            #_(GET "/worksheets" [worksheet-id :as req] (worksheets-page worksheet-id req))
            #_(POST "/worksheets" [& params :as req] (handle-worksheet-submit params req))
            #_(GET "/charts" req (charts-page req)))
          (routes
            (ANY "*" [] "you need to double auth!")))
        (routes
          (GET "*" [:as request]
            (response/found (str "/re-auth?return-url=" (codec/url-encode (request/request-url request)))))
          (POST "*" [] (auth-response/re-auth440))))
      (routes
        (ANY "*" [] "no such user")))))