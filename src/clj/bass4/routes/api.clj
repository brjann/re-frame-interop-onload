(ns bass4.routes.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.db-config :as db-config]
            [bass4.responses.messages :as messages-response]
            [bass4.treatment.responses :as treatment-response]
            [bass4.responses.privacy :as privacy-response]
            [bass4.responses.auth :as auth-response]
            [bass4.module.api :as module-api]
            [bass4.api-coercion :as api]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.session.timeout :as session-timeout]
            [bass4.utils :as utils]
            [bass4.i18n :as i18n]))

(defn treatment-mw
  [handler]
  (fn [request]
    (if-let [treatment (when-let [user (get-in request [:db :user])]
                         (treatment-builder/user-treatment (:user-id user)))]
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
    (fn [handler] (fn [request] (if (or (db-config/debug-mode?)
                                        (db-config/db-setting [:expose-swagger?] false))
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
                                                  "All api `post` and `put` requests to `/api/user/*` MUST include a CSRF token.\n\n"
                                                  "The token can be retrieved by making a request to `/api/user/csrf`\n\n"
                                                  "The token is included in the request header as `X-CSRF-Token`\n\n"
                                                  "The CSRF requirement can be disabled in debug mode for the current "
                                                  "session by making a request to `/api/user/disable-csrf`\n\n"
                                                  "## Dates and timezone\n"
                                                  "All dates are returned in UTC timezone by the API. "
                                                  "If the app wants to display them in the database's "
                                                  "timezone (rather than the browser's timezone), the database timezone name "
                                                  "(see https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) "
                                                  "can be retrieved at `/api/user/timezone-name` and dates "
                                                  "appropriately converted.\n\n"
                                                  "While it may be good to show for example message "
                                                  "send times in local date, treatment start and end "
                                                  "dates should be shown in database's timezone.")}}}}
    (context "/api" [:as request]

      (context "/session" []
        ;; This is a mock api declaration. session-timeout handles the responses
        (GET "/user-id" []
          :summary "Returns current user-id."
          :return {:user-id (s/maybe Long)}
          (throw (Exception. "This method should never be called.")))

        (GET "/status" []
          :summary "Returns number of seconds until session dies and needs re-authentication."
          :return (s/maybe {:hard    (s/maybe Long)
                            :re-auth (s/maybe Long)})
          (throw (Exception. "This method should never be called.")))

        (POST "/timeout-re-auth" []
          :summary "Forces session into re-auth timeout"
          :return {:result String}
          (throw (Exception. "This method should never be called.")))

        (POST "/timeout-hard" []
          :summary "Forces session into hard timeout (deletes the session)"
          :return {:result String}
          (throw (Exception. "This method should never be called.")))

        (POST "/timeout-hard-soon" []
          :summary "Forces session into hard timeout soon"
          :description (str "Forces session into hard timeout soon. Configured by :timeout-hard-soon setting,"
                            "which defaults to 1 hour.")
          :return {:result String}
          (throw (Exception. "This method should never be called.")))

        (POST "/renew" []
          :summary "Sets hard timeout to default value."
          :description (str "Sets hard timeout to default value. \n\n"
                            "Cannot be used for sessions that require re-auth to renew session.")
          :return {:result String}
          (throw (Exception. "This method should never be called."))))

      (POST "/logout" []
        :summary "Logout from session."
        :return {:result String}
        (auth-response/logout (:session request)))

      (GET "/logout-path" []
        :summary "Returns the path and path text after log out."
        :description "Returns the path and imperative text for the path after the user has logged out."
        :return {:path String
                 :text String}
        (let [session (:session request)
              path    (or (:logout-path session) "/login")
              text    (i18n/tr [(or (:logout-path-text-key session)
                                    :return-to-login)])]
          (http-response/ok {:path path
                             :text text})))

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
            :return [module-api/ModuleWithContent]
            (module-api/modules-list
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-main/:module-id" []
            :summary "Main text of module."
            :path-params [module-id :- s/Int]
            :return module-api/MainText
            (module-api/main-text
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-homework/:module-id" []
            :summary "Homework of module."
            :path-params [module-id :- s/Int]
            :return module-api/Homework
            (module-api/homework
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (PUT "/module-homework-submit" []
            :summary "Mark homework as submitted."
            :body-params [module-id :- s/Int]
            :return {:result String}
            (module-api/homework-submit
              module-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/module-worksheet/:module-id/:worksheet-id" []
            :summary "Homework of module."
            :path-params [module-id :- s/Int
                          worksheet-id :- s/Int]
            :return module-api/Worksheet
            (module-api/worksheet
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
            (module-api/module-content-access
              module-id
              content-id
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          #_(PUT "/activate-module" []
              :summary "Grants user access to a module."
              :body-params [module-id :- s/Int]
              :return {:result String}
              (module-api/activate-module
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
            (module-api/get-module-content-data
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
            (module-api/save-module-content-data
              module-id
              content-id
              data
              (:modules (:tx-components treatment))
              (:treatment-access-id treatment-access)))

          (GET "/content-data-namespaces" []
            :summary "Get all content data namespaces that have data for user."
            :return [String]
            (module-api/get-content-data-namespaces
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
            (module-api/get-content-data
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
            (module-api/save-content-data
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