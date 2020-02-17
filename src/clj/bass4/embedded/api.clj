(ns bass4.embedded.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.embedded.middleware :as embedded-mw]
            [bass4.layout :as layout]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.responses.messages :as messages-response]
            [bass4.treatment.responses :as treatment-response]
            [bass4.responses.privacy :as privacy-response]
            [bass4.responses.auth :as auth-response]
            [bass4.module.api :as module-api]
            [bass4.api-coercion :as api]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.session.timeout :as session-timeout]
            [bass4.utils :as utils]
            [bass4.i18n :as i18n]
            [bass4.clients.core :as clients]
            [bass4.error-pages :as error-pages]
            [bass4.treatment.services :as treatment-service]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (java.util Map)))

(defn ->int
  [i]
  (let [x (cond
            (nil? i)
            nil

            (integer? i)
            i

            (re-find #"^\d+$" i)
            (read-string i)

            :else
            nil)]
    x))

(defn user-treatment-mw
  [handler]
  (fn [request]
    (let [user-id             (->int (get-in request [:params :user-id]))
          treatment-access-id (->int (get-in request [:params :treatment-access-id]))]
      (cond
        (or (nil? user-id) (nil? treatment-access-id))
        (http-response/bad-request "Request must include user-id and treatment-request-id as params")

        (not (embedded-mw/authorized? request [:user-id user-id]))
        (http-response/forbidden (str "You are not authorized to access user " user-id))

        :else
        (let [treatment-access (->> (treatment-service/user-treatment-accesses user-id)
                                    (some #(when (= treatment-access-id (:treatment-access-id %)) %)))]
          (cond
            (nil? treatment-access)
            (http-response/not-found (str "Treatment access " treatment-access-id " not found on user " user-id))

            (not (embedded-mw/authorized? request [:treatment-id (:treatment-id treatment-access)]))
            (http-response/forbidden (str "You are not authorized to access treatment " (:treatment-id treatment-access)))

            :else
            (let [treatment-map  (treatment-builder/treatment-map (:treatment-id treatment-access))
                  user-treatment {:user-id          user-id
                                  :treatment-access treatment-access
                                  :tx-components    (treatment-builder/tx-components treatment-access treatment-map)
                                  :treatment        treatment-map}]
              (handler (-> request
                           (assoc-in [:db :user-treatment] user-treatment))))))))))

(defn api-tx-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/embedded/api/user-tx/*"]
    #'user-treatment-mw))

(def api-routes
  (context "/embedded/api" [:as request]
    (GET "/unlock-api" []
      :summary "Gives full permissions for all /embedded/api requests. Can only be used in dev or debug mode."
      :return String
      (if (clients/debug-mode?)
        (-> (http-response/ok "You have access")
            (assoc :session (merge (:session request)
                                   {:csrf-disabled           true
                                    ::embedded-mw/debug-api? true
                                    ::embedded-mw/allow-all? true})))
        (http-response/forbidden "Not in debug or dev mode")))
    (GET "/test" []
      :summary ""
      (layout/text-response "HELLO!"))
    (context "/user-tx" [:as
                         {{:keys [user-treatment]}                     :db
                          {{:keys [treatment-access]} :user-treatment} :db}]
      :query-params [user-id :- s/Int
                     treatment-access-id :- s/Int]
      (GET "/modules" []
        :summary "All modules in treatment with treatment content info."
        :return [module-api/ModuleWithContent]
        (module-api/modules-list
          (:modules (:tx-components user-treatment))
          treatment-access-id)))

    #_(context "/tx" [:as
                      {{:keys [treatment]}                     :db
                       {{:keys [treatment-access]} :treatment} :db}]

        (GET "/treatment-info" []
          :summary "Info about available treatment components."
          :return treatment-response/TreatmentInfo
          (treatment-response/api-tx-info user treatment))

        ;; --------------
        ;;    MODULES
        ;; --------------

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
          :summary "Worksheet of module."
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
          (messages-response/api-message-read (:user-id user) message-id)))))