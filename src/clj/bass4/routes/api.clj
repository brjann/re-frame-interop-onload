(ns bass4.routes.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [bass4.services.treatment :as treatment-service]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.db-config :as db-config]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.treatment :as treatment-response]
            [bass4.responses.privacy :as privacy-response]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.modules :as modules-response]))

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

(defn swagger-mw
  "Limits access to Swagger UI and json to only dev and debug mode."
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/swagger*"]
    (fn [handler] (fn [request] (if (db-config/debug-mode?)
                                  (handler request)
                                  (http-response/not-found))))
    #'treatment-mw))


(s/defschema User {:name s/Str
                   :sex  (s/enum :male :female)})

(def api-routes
  (api
    {:swagger {:ui   "/swagger-ui"
               :spec "/swagger.json"
               :data {:info {:title       "BASS API"
                             :description (str "# Notes\n"
                                               "## Only for logged in users\n"
                                               "Currently, the api is only available for a user who is logged in. "
                                               "Login is done through BASS and all API requests are received in the "
                                               "context of the current user (through a session cookie). Thus, there "
                                               "is no need (or possible) to submit the identity of the current user "
                                               "as part of the api requests.\n\n"
                                               "## Status 440\n"
                                               "All api requests MUST be prepared to handle a status 440 response. "
                                               "This response means that the user's session has timed out and they need "
                                               "to re-authenticate.\n\n"
                                               "When a 440 response is returned, the app needs to ask the user for their "
                                               "password and submit the password to `/api/re-auth`\n\n"
                                               "## CSRF token\n"
                                               "All api `post` requests to `/user/*` MUST include a CSRF token.\n\n"
                                               "The token can be retrieved by making a request to `/user/csrf`\n\n"
                                               "The token is included in the request header as `X-CSRF-Token`\n\n"
                                               "The CSRF requirement can be disabled in debug mode for the current "
                                               "session by making a request to `/user/disable-csrf`")}}}}
    (context "/api" [:as request]

      (POST "/re-auth" []
        :summary "Re-authenticate after timeout."
        :body-params [password :- String]
        :return {:result String}
        (auth-response/check-re-auth-api (:session request) password))

      (context "/user" [:as {{:keys [user]} :db}]
        (GET "/csrf" []
          :summary "Session's CSRF token. Must be included in all posts in header or body."
          :return String
          (layout/text-response (treatment-response/csrf)))

        (GET "/disable-csrf" []
          :summary "Removes the CSRF requirement for the current session. Can only be used in dev or debug mode."
          (if (db-config/debug-mode?)
            (-> (http-response/ok)
                (assoc :session (assoc (:session request) :csrf-disabled true)))
            (http-response/forbidden "Not in debug or dev mode")))

        (GET "/privacy-notice-html" []
          :summary "Database's privacy notice in HTML format."
          :return String
          (privacy-response/privacy-notice-html user))

        (GET "/timezone-name" []
          :summary "Name of the database's timezone."
          :return String
          (layout/text-response (db-config/time-zone)))

        (context "/tx" [:as
                        {{:keys [treatment]}                     :db
                         {{:keys [treatment-access]} :treatment} :db}]

          (GET "/treatment-info" []
            :summary "Info about available treatment components."
            :return treatment-response/TreatmentInfo
            (treatment-response/api-tx-info user treatment))

          (GET "/modules" []
            :summary "All modules in treatment with treatment content info."
            :return [modules-response/ModuleWithContent]
            (modules-response/api-modules-list
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-main/:module-id" [module-id]
            :summary "Main text of module."
            :return modules-response/MainText
            (modules-response/api-main-text
              module-id
              (:modules (:tx-components treatment))))

          (GET "/module-content-data/:module-id/:content-id"
               [module-id content-id]
            :summary "Get content data belonging to namespaces within a module and content."
            :description (str "ALIASED\n\n"
                              "Returns json in format:\n\n"
                              "    {\"namespace1\": {\"key1\": \"value1\"\n"
                              "                    \"key2\": \"value2\"}\n"
                              "    {\"namespace2\": {\"key3\": \"value3\"\n"
                              "                    \"key4\": \"value4\"}\n")
            :query-params [namespaces :- [String]]
            :return (s/maybe {String {String String}})
            (modules-response/api-get-content-data
              namespaces
              (:treatment-access-id treatment-access)))

          (GET "/content-data/" []
            :summary "Get content data belonging to namespaces."
            :description (str "Returns json in format:\n\n"
                              "    {\"namespace1\": {\"key1\": \"value1\"\n"
                              "                    \"key2\": \"value2\"}\n"
                              "    {\"namespace2\": {\"key3\": \"value3\"\n"
                              "                    \"key4\": \"value4\"}\n")
            :query-params [namespaces :- [String]]
            :return (s/maybe {String {String String}})
            (modules-response/api-get-content-data
              namespaces
              (:treatment-access-id treatment-access)))

          (POST "/content-data" []
            :summary "Save content data."
            :description (str "Expects json in format:\n\n"
                              "    {\"namespace1$key1\": \"value1\"\n"
                              "     \"namespace1$key2\": \"value2\"\n"
                              "     \"namespace2$key3\": \"value3\"\n"
                              "     \"namespace2$key4\": \"value4\"}")
            :body-params {String String}
            :return {:result String}
            (modules-response/api-get-content-data
              namespaces
              (:treatment-access-id treatment-access)))

          (GET "/messages" []
            :summary "All messages for patient."
            :return [messages-response/Message]
            (messages-response/api-messages user))

          (POST "/new-message" []
            :summary "Send new message."
            :body-params [message :- String]
            :return {:result String}
            (messages-response/api-save-message (:user-id user) message))

          (POST "/message-read" []
            :summary "Mark message with message id as read."
            :body-params [message-id :- s/Int]
            :return {:result String}
            (messages-response/api-message-read (:user-id user) message-id))

          (POST "/content-accessed" []
            :summary "Mark content as accessed by user."
            :body-params [content-id :- s/Int]
            :return {:result String}
            (http-response/ok {:result "ok"})))))))


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