(ns bass4.routes.user-api
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.user :as user-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.responses.dashboard :as dashboard]
            [bass4.responses.error-report :as error-report-response]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.modules :as modules-response]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [bass4.http-utils :as h-utils]))

(defn api-tx-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/api/tx/*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules user-routes/tx-rules}
                             {:uri   "/user/api/tx/message*"
                              :rules user-routes/tx-message-rules}])
    #'user-response/treatment-mw))

(defn api-response-mw
  "Returns only status code for error responses
  (i.e., strips body)"
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/api/*"]
    (fn [handler]
      (fn [request]
        (let [response (handler request)]
          (cond
            (<= 400 (:status response))
            {:status (:status response)}

            (map? response)
            (h-utils/json-response response)

            :else
            response))))))


; -----------------------
;          ROUTES
; -----------------------

(defroutes api-routes
  (context "/user/api" [:as {{:keys [user]} :db}]
    (GET "/privacy-notice" []
      (user-response/privacy-notice-bare user)))
  (context "/user/api/tx" [:as
                           {{:keys [render-map treatment user]}     :db
                            {{:keys [treatment-access]} :treatment} :db
                            :as                                     request}]
    (GET "/" []
      (dashboard/dashboard user (:session request) render-map treatment))

    (GET "/privacy-notice" []
      (user-response/privacy-notice-page user render-map))

    ;; ERROR REPORT
    (GET "/error-report" []
      (error-report-response/error-report-page render-map user))
    (POST "/error-report" [error-description]
      (error-report-response/handle-error-report user error-description))

    ;; MESSAGES
    (GET "/messages" []
      (messages-response/api-messages treatment user))
    (POST "/messages" [text]
      (messages-response/save-message (:user-id user) text))
    (POST "/message-save-draft" [text]
      (messages-response/save-draft (:user-id user) text))
    (POST "/message-read" [message-id]
      (messages-response/message-read (:user-id user) message-id))

    ;; MODULES
    (GET "/modules" []
      (modules-response/modules-list
        render-map
        (:modules (:user-components treatment))
        (:treatment-access-id treatment-access)))
    (context "/module/:module-id" [module-id]
      ;; This is maybe a bit dirty,
      ;; but it's nothing compared to the previous chaos.
      (if-let [module (->> (get-in treatment [:user-components :modules])
                           (filter #(= (str->int module-id) (:module-id %)))
                           (some #(and (:active %) %)))]
        (routes
          (GET "/" [] (modules-response/main-text treatment-access render-map module))
          (POST "/" [content-data]
            (modules-response/save-main-text-data treatment-access content-data))
          (GET "/homework" []
            (modules-response/homework treatment-access render-map module))
          (POST "/homework" [content-data submit?]
            (modules-response/save-homework treatment-access module content-data submit?))
          (POST "/retract-homework" []
            (modules-response/retract-homework treatment-access module))
          (GET "/worksheet/:worksheet-id" [worksheet-id]
            (modules-response/worksheet
              treatment-access
              render-map
              module
              worksheet-id))
          (GET "/worksheet/:worksheet-id/example" [worksheet-id return-path]
            (modules-response/worksheet-example module worksheet-id return-path)))
        ;; Module not found
        (layout/error-404-page (i18n/tr [:modules/no-module]))))
    (POST "/content-data" [content-data]
      (modules-response/save-worksheet-data
        (get-in treatment [:treatment-access :treatment-access-id])
        content-data))))