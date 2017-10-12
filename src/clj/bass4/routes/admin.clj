(ns bass4.routes.admin
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.utils :refer [str->int]]
            [bass4.services.bass :as bass]))


;; TODO: Wrap only user requests in timeout/re-auth
(def admin-routes
  (context "/admin" [:as request]
    (GET "/create-session" [& params]
      (if (and (:uid params) (:redirect params))
        (when-let [user-id (bass/admin-session-file (:uid params))]
          (-> (response/found (:redirect params))
              (assoc :session {:admin user-id :identity user-id})))
        (layout/text-response "Missing uid or redirect params.")))
    (if (get-in request [:session :admin])
      (routes
        (POST "/instrument/:instrument-id" [instrument-id & params]
          (instruments/post-answers (str->int instrument-id) (:items params) (:specifications params)))
        (GET "/instrument/:instrument-id" [instrument-id]
          (instruments/instrument-page instrument-id))
        (GET "/instrument/summary/:instrument-id" [instrument-id]
          (instruments/summary-page instrument-id)))
      (routes
        (ANY "*" [] (layout/error-403-page))))))