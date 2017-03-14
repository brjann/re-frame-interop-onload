(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST]]
            [bass4.responses.messages :as messages-response]))

(defroutes user-routes
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