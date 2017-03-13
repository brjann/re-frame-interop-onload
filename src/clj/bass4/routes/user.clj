(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST]]
            [bass4.services.messages :as m]))

(defroutes user-routes
  (context "/user" []
    #_(GET "/" req (dashboard-page req))
    #_(GET "/profile" [errors :as req] (profile-page req errors))
    #_(GET "/modules" req (modules-page req))
    #_(GET "/worksheets" [worksheet-id :as req] (worksheets-page worksheet-id req))
    #_(POST "/worksheets" [& params :as req] (handle-worksheet-submit params req))
    (GET "/messages" [errors :as req] (m/messages-page req errors))
    (POST "/messages" [& params :as req] (m/new-message! params req))
    (POST "/message-save-draft" [& params :as req] (m/x-save-draft! params req))
    #_(GET "/charts" req (charts-page req))))