(ns bass4.routes.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [bass4.responses.user :as user-response]
            [bass4.services.treatment :as treatment-service]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.db-config :as db-config]
            [bass4.responses.messages :as messages-response]
            [compojure.api.exception :as ex]
            [ring.util.http-response :as http-response]
            [compojure.api.coercion.core :as cc])
  (:import (org.joda.time DateTime)))

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
    ["/api/user/tx/*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules [[#'user-routes/consent-needed? "/user/privacy/consent" :ok]
                                      [#'user-routes/assessments-pending? "/user/assessments" :ok]
                                      [#'user-routes/limited-access? "/escalate" :ok]]}
                             {:uri   "/api/user/tx/message*"
                              :rules user-routes/tx-message-rules}])
    #'treatment-mw))

(defn response-validation-handler
  "Creates error response based on a response error. The following keys are available:

    :type            type of the exception (::response-validation)
    :coercion        coercion instance used
    :in              location of the value ([:response :body])
    :schema          schema to be validated against
    :error           schema error
    :request         raw request
    :response        raw response"
  [e data req]
  (http-response/internal-server-error
    (-> data
        (dissoc :request :response :value)
        (update :coercion cc/get-name)
        #_(assoc :value (-> data :response :body))
        (->> (cc/encode-error (:coercion data))))))

(s/defschema Message
  {:message-id    s/Int
   :unread?       Boolean
   :text          String
   :send-datetime DateTime
   :sender-name   String
   :sender-type   String})

(def api-routes
  (api
    {:swagger    {:ui   "/swagger-ui"
                  :spec "/swagger.json"
                  :data {:info {:version     "1.0.0"
                                :title       "BASS API"
                                :description "XXX"}}}
     :exceptions {:handlers
                  {::ex/response-validation response-validation-handler}}}
    (context "/api" []
      (context "/user" [:as {{:keys [user]} :db}]
        (GET "/privacy-notice-html" []
          (user-response/privacy-notice-html user))
        (GET "/timezone-name" []
          (str (db-config/time-zone)))

        (context "/tx" [:as
                        {{:keys [treatment]}                     :db
                         {{:keys [treatment-access]} :treatment} :db
                         :as                                     request}]
          (GET "/messages" []
            :summary "list all messages for patient"
            :return [Message]
            (messages-response/api-messages user)))))))


#_(context "/api" []
    (GET "/authenticated" []
      :auth-rules authenticated?
      :current-identity identity
      (ok {:user-id identity}))

    (GET "/req" req
      (str req))

    (POST "/login" req
      :body-params [username :- String, password :- String]
      :summary "log in the user and create a session"
      (auth/login! req username password))

    (GET "/logout" []
      :summary "remove user session"
      :return Result
      (auth/logout!))

    (context "/user" []
      :auth-rules authenticated?
      :tags ["user"]

      (GET "/messages" []
        :current-identity user-id
        :summary "list messages for a patient"
        :return [Message]
        (messages/api-list-messages user-id))

      (GET "/message-draft" []
        :current-identity user-id
        :summary "get message draft for a patient"
        :return (s/maybe MessageDraft)
        (messages/api-draft user-id))

      (GET "/all-modules" []
        :current-identity user-id
        :summary "list modules for a patient"
        ;:return [Message]
        (response/ok {}))

      (POST "/message" []
        :current-identity user-id
        :body-params [subject :- String, text :- String]
        :summary "posts a new message"
        :return Result
        (messages/api-new-message! user-id subject text))

      (POST "/draft" []
        :current-identity user-id
        :body-params [subject :- String, text :- String]
        :summary "save a message draft"
        :return Result
        (messages/api-save-draft! user-id subject text))

      (GET "/administrations" []
        :current-identity user-id
        :summary "get current administrations"
        ;;:return [Administration]
        (administrations/get-administrations user-id))

      (GET "/instrument" []
        :current-identity user-id
        :summary "get current administrations"
        ;;:return [Administration]
        (instruments/get-instrument 1658)))
    #_(GET "/instrument" [instrument-id]
        :query-params [instrument-id :- String]
        :current-identity user-id
        :summary "get instrument by id"
        ;;:return [Administration]
        (instruments/get-instrument instrument-id)))