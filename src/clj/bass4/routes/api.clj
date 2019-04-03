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
            [bass4.responses.modules :as modules-response]
            [bass4.api-coercion :as api]))

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
    {:exceptions {:handlers {:bass4.api-coercion/api-exception (fn [^Exception e _ _]
                                                                 (api/api-exception-response e))}}
     :swagger    {:ui   "/swagger-ui"
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

      (GET "/logout" []
        :summary "Logout from session."
        :return {:result String}
        (auth-response/logout))

      (POST "/re-auth" []
        :summary "Re-authenticate after timeout."
        :description (str "After a 440 response, the app MUST ask the user to re-authenticate "
                          "towards this endpoint.\n\n"
                          "# HTTP status error responses\n\n"
                          "## 422\n"
                          "Wrong password.\n\n"
                          "## 429\n"
                          "After too many failed attempts - BASS will respond with 429 Too many requests "
                          "and the user will need to wait a few seconds before attempting again. "
                          "The minimum number of seconds to wait is given in the body of the response.")
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

        (GET "/privacy-notice" []
          :summary "Database's privacy notice in raw (markdown) format."
          :return String
          (privacy-response/privacy-notice-raw user))

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

          ;; --------------
          ;;    MODULES
          ;; --------------

          (GET "/modules" []
            :summary "All modules in treatment with treatment content info."
            :return [modules-response/ModuleWithContent]
            (modules-response/api-modules-list
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-main/:module-id" []
            :summary "Main text of module."
            :path-params [module-id :- s/Int]
            :return modules-response/MainText
            (modules-response/api-main-text
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-homework/:module-id" []
            :summary "Homework of module."
            :path-params [module-id :- s/Int]
            :return modules-response/Homework
            (modules-response/api-homework
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (PUT "/module-homework-submit" []
            :summary "Mark homework as submitted."
            :body-params [module-id :- s/Int]
            :return {:result String}
            (modules-response/api-homework-submit
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-worksheet/:module-id/:worksheet-id" []
            :summary "Homework of module."
            :path-params [module-id :- s/Int
                          worksheet-id :- s/Int]
            :return modules-response/Worksheet
            (modules-response/api-worksheet
              module-id
              worksheet-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (PUT "/module-content-accessed" []
            :summary "Mark content as accessed by user."
            :description (str "Mark content as accessed by user. "
                              "Should be called the first time a user accesses the content "
                              "(i.e., when the `accessed?` property is false.")
            :body-params [module-id :- s/Int
                          content-id :- s/Int]
            :return {:result String}
            (modules-response/api-module-content-access
              module-id
              content-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (PUT "/activate-module" []
            :summary "Grants user access to a module."
            :body-params [module-id :- s/Int]
            :return {:result String}
            (modules-response/api-activate-module
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))


          ;; --------------
          ;;  CONTENT DATA
          ;; --------------

          (GET "/module-content-data/:module-id/:content-id" []
            :summary "Get content data belonging to namespaces within a module and content."
            :description (str "Returns data with aliased namespaces in format\n\n"
                              "    {\"namespace1\": {\"key1\": \"value1\"\n"
                              "                    \"key2\": \"value2\"}\n"
                              "     \"namespace2\": {\"key3\": \"value3\"\n"
                              "                    \"key4\": \"value4\"}}\n")
            :path-params [module-id :- s/Int
                          content-id :- s/Int]
            :return (s/maybe {String {String String}})
            (modules-response/api-get-module-content-data
              module-id
              content-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (PUT "/module-content-data/:module-id/:content-id" []
            :summary "Save content data belonging to content within a module."
            :description (str "Saves data and handles aliased namespaces\n\n"
                              "Expects data in format:\n\n"
                              "    {\"data\": {\"namespace1\": {\"key1\": \"value1\",\n"
                              "                             \"key2\": \"value2\"},\n"
                              "              \"namespace2\": {\"key3\": \"value3\",\n"
                              "                             \"key4\": \"value4\"}}}\n")
            :path-params [module-id :- s/Int content-id :- s/Int]
            :body-params [data]
            ;:return {:result String}
            (modules-response/api-save-module-content-data
              module-id
              content-id
              data
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/content-data" []
            :summary "Get content data belonging to namespaces."
            :description (str "Provides direct access to content data without "
                              "handling module content aliasing\n\n"
                              "Returns data in format:\n\n"
                              "    {\"namespace1\": {\"key1\": \"value1\"\n"
                              "                    \"key2\": \"value2\"}\n"
                              "     \"namespace2\": {\"key3\": \"value3\"\n"
                              "                    \"key4\": \"value4\"}}\n")
            :query-params [namespaces :- [String]]
            :return (s/maybe {String {String String}})
            (modules-response/api-get-content-data
              namespaces
              (:treatment-access-id treatment-access)))

          (PUT "/content-data" []
            :summary "Save content data."
            :description (str "Provides writing directly to content data without "
                              "handling module content aliasing\n\n"
                              "Expects data in format:\n\n"
                              "    {\"data\": {\"namespace1\": {\"key1\": \"value1\",\n"
                              "                             \"key2\": \"value2\"},\n"
                              "              \"namespace2\": {\"key3\": \"value3\",\n"
                              "                             \"key4\": \"value4\"}}}\n")
            :body-params [data]
            :return {:result String}
            (modules-response/api-save-content-data
              data
              (:treatment-access-id treatment-access)))

          ;; --------------
          ;;    MESSAGES
          ;; --------------

          (GET "/messages" []
            :summary "All messages for patient."
            :return [messages-response/Message]
            (messages-response/api-messages user))

          (POST "/message" []
            :summary "Send new message."
            :body-params [message :- String]
            :return {:result String}
            (messages-response/api-save-message (:user-id user) message))

          (PUT "/message-read" []
            :summary "Mark message with message id as read."
            :body-params [message-id :- s/Int]
            :return {:result String}
            (messages-response/api-message-read (:user-id user) message-id)))))))