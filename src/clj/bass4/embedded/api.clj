(ns bass4.embedded.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.embedded.middleware :as embedded-mw]
            [bass4.route-rules :as route-rules]
            [bass4.responses.messages :as messages-response]
            [bass4.treatment.responses :as treatment-response]
            [bass4.module.api :as module-api]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.clients.core :as clients]
            [bass4.treatment.services :as treatment-service]
            [bass4.services.user :as user-service]
            [bass4.services.messages :as messages]))

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

        (not (embedded-mw/authorized? request [:treatment/user-id user-id]))
        (http-response/forbidden (str "You are not authorized to access user " user-id))

        :else
        (let [user             (user-service/get-user user-id)
              treatment-access (->> (treatment-service/user-treatment-accesses user-id)
                                    (some #(when (= treatment-access-id (:treatment-access-id %)) %)))]
          (cond
            (nil? user)
            (http-response/not-found (str "User " user-id " not found"))

            (nil? treatment-access)
            (http-response/not-found (str "Treatment access " treatment-access-id " not found on user " user-id))

            ;(not (embedded-mw/authorized? request [:treatment-id (:treatment-id treatment-access)]))
            ;(http-response/forbidden (str "You are not authorized to access treatment " (:treatment-id treatment-access)))

            :else
            (let [treatment-map  (treatment-builder/treatment-map (:treatment-id treatment-access))
                  user-treatment {:new-messages?    (messages/new-messages? user-id)
                                  :treatment-access treatment-access
                                  :tx-components    (treatment-builder/tx-components treatment-access treatment-map)
                                  :treatment        treatment-map}]
              (handler (-> request
                           (assoc-in [:api-request :user-treatment] user-treatment)
                           (assoc-in [:api-request :user] user))))))))))

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

    (context "/user-tx" [:as {{:keys [user-treatment user]} :api-request}]
      :query-params [user-id :- s/Int
                     treatment-access-id :- s/Int]

      ;; --------------
      ;;    MODULES
      ;; --------------

      (GET "/modules" []
        :summary "All modules in treatment with treatment content info."
        :return [module-api/ModuleWithContent]
        (module-api/modules-list
          (:modules (:tx-components user-treatment))
          treatment-access-id))

      (GET "/treatment-info" []
        :summary "Info about available treatment components."
        :return treatment-response/TreatmentInfo
        (treatment-response/api-tx-info user user-treatment))

      (GET "/module-main/:module-id" []
        :summary "Main text of module."
        :path-params [module-id :- s/Int]
        :return module-api/MainText
        (module-api/main-text
          module-id
          (:modules (:tx-components user-treatment))
          treatment-access-id))

      (GET "/module-homework/:module-id" []
        :summary "Homework of module."
        :path-params [module-id :- s/Int]
        :return module-api/Homework
        (module-api/homework
          module-id
          (:modules (:tx-components user-treatment))
          treatment-access-id))

      (GET "/module-worksheet/:module-id/:worksheet-id" []
        :summary "Worksheet of module."
        :path-params [module-id :- s/Int
                      worksheet-id :- s/Int]
        :return module-api/Worksheet
        (module-api/worksheet
          module-id
          worksheet-id
          (:modules (:tx-components user-treatment))
          treatment-access-id))

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
          (:modules (:tx-components user-treatment))
          treatment-access-id))

      (GET "/content-data-namespaces" []
        :summary "Get all content data namespaces that have data for user."
        :return [String]
        (module-api/get-content-data-namespaces treatment-access-id))

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
          treatment-access-id))

      ;; --------------
      ;;    MESSAGES
      ;; --------------

      (GET "/messages" []
        :summary "All messages for patient."
        :return [messages-response/Message]
        (messages-response/api-messages user))

      (GET "/user-id" []
        :summary "Returns the request user-id. Dummy function."
        :return {:result s/Int}
        (http-response/ok {:result user-id})))))