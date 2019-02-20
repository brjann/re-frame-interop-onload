(ns bass4.routes.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.responses.user :as user-response]
            [bass4.services.treatment :as treatment-service]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [clojure.tools.logging :as log]
            [bass4.http-utils :as h-utils]
            [bass4.db-config :as db-config]))

(defn treatment-mw
  [handler]
  (fn [request]
    (if-let [treatment (when-let [user (get-in request [:db :user])]
                         (treatment-service/user-treatment (:user-id user)))]
      (handler (-> request
                   (assoc-in [:db :treatment] treatment)))
      (handler request))))

(defn api-tx-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/api/user/tx" "/api/user/tx/*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules user-routes/tx-rules}
                             {:uri   "/user/tx/message*"
                              :rules user-routes/tx-message-rules}])
    #'treatment-mw))

(def api-routes
  (api
    {:swagger {:ui   "/swagger-ui"
               :spec "/swagger.json"
               :data {:info {:version     "1.0.0"
                             :title       "BASS API"
                             :description "XXX"}}}}
    (context "/api" []
      (context "/user" [:as {{:keys [user]} :db}]
        (GET "/privacy-notice-html" []
          (user-response/privacy-notice-html user))
        (GET "/timezone-name" []
          (str (db-config/time-zone)))
        (context "/tx" [:as request]
          (GET "/" []
            (http-response/ok (get-in request [:db]))))))))