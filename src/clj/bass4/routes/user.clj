(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.dashboard :as dashboard]
            [bass4.treatment.responses :as user-response]
            [bass4.module.responses :as modules-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.auth :as auth-response]
            [bass4.assessment.responses :as assessments-response]
            [bass4.i18n :as i18n]
            [bass4.route-rules :as route-rules]
            [bass4.middleware.core :as middleware]
            [bass4.services.privacy :as privacy-service]
            [bass4.responses.error-report :as error-report-response]
            [bass4.config :as config]
            [bass4.file-response :as file]
            [bass4.responses.privacy :as privacy-response]
            [bass4.session.timeout :as session-timeout]))


; -----------------------
;     RULE PREDICATES
; -----------------------

(defn- consent-needed?
  [request _]
  (let [user (get-in request [:db :user])]
    (cond
      (:privacy-notice-consent-time user)
      false

      (privacy-service/privacy-notice-disabled?)
      false

      :else
      (privacy-service/user-must-consent? (:project-id user)))))

(defn- assessments-pending?
  [{:keys [session]} _]
  (:assessments-pending? session))

(defn no-treatment-no-assessments?
  [{:keys [session] {:keys [treatment]} :db} _]
  (and (not treatment) (not (:assessments-performed? session))))

(defn no-treatment-but-assessments?
  [{:keys [session] {:keys [treatment]} :db} _]
  (and (not treatment) (:assessments-performed? session)))

(defn double-auth?
  [{:keys [session]} _]
  (auth-response/need-double-auth? session))

(defn logged-in?
  [{:keys [session]} _]
  (:user-id session))

(defn messages?
  [{{:keys [treatment]} :db} _]
  (get-in treatment [:tx-components :messaging?]))

(defn send-messages?
  [{{:keys [treatment]} :db :as request} _]
  (if (= :post (:request-method request))
    (get-in treatment [:tx-components :send-messages?])
    true))

(defn limited-access?
  [{:keys [session]} _]
  (:limited-access? session))


; -----------------------
;    ROUTES MIDDLEWARE
; -----------------------

(def tx-message-rules
  [[#'messages? :ok 404]
   [#'send-messages? :ok 404]])

(defn user-routes-mw
  "Middleware for all /user routes"
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/*" "/user" "/api/user/*"]
    #'assessments-response/check-assessments-mw
    #'session-timeout/wrap-session-re-auth-timeout
    #'middleware/wrap-csrf
    #'auth-response/double-auth-mw
    #'auth-response/restricted-mw))

(defn user-tx-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/tx" "/user/tx/*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules [[#'consent-needed? "/user/privacy/consent" :ok]
                                      [#'assessments-pending? "/user/assessments" :ok]
                                      [#'no-treatment-no-assessments? "/to-no-activities" :ok]
                                      [#'no-treatment-but-assessments? "/to-activities-finished" :ok]
                                      [#'limited-access? "/escalate" :ok]]}
                             {:uri   "/user/tx/message*"
                              :rules tx-message-rules}])
    #'user-response/treatment-mw))

(defn privacy-consent-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/privacy/*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules [[#'consent-needed? :ok "/user"]]}])))

(defn assessment-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user/assessments*"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules [[#'consent-needed? "/user/privacy/consent" :ok]]}])))

(defn root-reroute-mw
  "Rerouting when accessing /user/"
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user" "/user/"]
    (route-rules/wrap-rules [{:uri   "*"
                              :rules [[#'consent-needed? "/user/privacy/consent" :ok]
                                      [#'assessments-pending? "/user/assessments" :ok]
                                      [#'no-treatment-no-assessments? "/to-no-activities" :ok]
                                      [#'no-treatment-but-assessments? "/to-activities-finished" :ok]]}])
    #'user-response/treatment-mw))

; -----------------------
;          ROUTES
; -----------------------

(defroutes pluggable-ui
  (context "/user/ui" [:as request]
    (GET "*" [] (let [uri      (:uri request)
                      path     (subs uri (count "/user/ui"))
                      ui-path  (config/env :pluggable-ui-path)
                      _        (when-not ui-path
                                 (throw (Exception. "No :pluggable-ui-path in config")))
                      response (or (http-response/file-response path {:root ui-path})
                                   (http-response/file-response "" {:root ui-path}))]
                  (if (= 200 (:status response))
                    (file/file-headers response)
                    response)))
    (POST "*" [] (http-response/bad-request "Cannot post to pluggable ui"))))

(defroutes root-reroute
  (context "/user" []
    (GET "/" [] (http-response/found "/user/tx"))))

(defroutes assessment-routes
  (context "/user/assessments" [:as {{:keys [user]} :db
                                     :as            request}]
    (GET "/" [] (assessments-response/handle-assessments (:user-id user) (:session request)))
    (POST "/" [instrument-id items specifications]
      (assessments-response/post-instrument-answers
        (:user-id user)
        (:session request)
        instrument-id
        items
        specifications))))

(defroutes privacy-consent-routes
  (context "/user/privacy" [:as {{:keys [user]} :db}]
    (GET "/consent" []
      (privacy-response/privacy-consent-page user))
    (POST "/consent" [i-consent]
      (privacy-response/handle-privacy-consent user i-consent))))

(defroutes tx-routes
  (context "/user/tx" [:as
                       {{:keys [render-map treatment user]}     :db
                        {{:keys [treatment-access]} :treatment} :db
                        :as                                     request}]
    (GET "/" []
      (dashboard/dashboard user (:session request) render-map treatment))

    (GET "/privacy-notice" []
      (privacy-response/privacy-notice-page user render-map))

    ;; ERROR REPORT
    (GET "/error-report" []
      (error-report-response/error-report-page render-map user))
    (POST "/error-report" [error-description]
      (error-report-response/handle-error-report user error-description))

    ;; MESSAGES
    (GET "/messages" []
      (messages-response/messages-page render-map user))
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
        (:modules (:tx-components treatment))
        (:treatment-access-id treatment-access)))
    (context "/module/:module-id" [module-id]
      ;; This is maybe a bit dirty,
      ;; but it's nothing compared to the previous chaos.
      (if-let [module (->> (get-in treatment [:tx-components :modules])
                           (filter #(= (str->int module-id) (:module-id %)))
                           (some #(and (:active? %) %)))]
        (routes
          (GET "/" [] (modules-response/main-text treatment-access render-map module))
          (POST "/" [content-data]
            (modules-response/save-main-text-data
              (:treatment-access-id treatment-access)
              module
              content-data))

          (GET "/homework" []
            (modules-response/homework treatment-access render-map module))
          (POST "/homework" [content-data submit?]
            (modules-response/save-homework
              (:treatment-access-id treatment-access)
              module
              content-data
              submit?))

          (POST "/retract-homework" []
            (modules-response/retract-homework treatment-access module))

          (GET "/worksheet/:worksheet-id" [worksheet-id]
            (modules-response/worksheet
              treatment-access
              render-map
              module
              worksheet-id))
          (POST "/worksheet/:worksheet-id" [worksheet-id content-data]
            (modules-response/save-worksheet-data
              (:treatment-access-id treatment-access)
              module
              worksheet-id
              content-data))

          (GET "/worksheet/:worksheet-id/example" [worksheet-id return-path]
            (modules-response/worksheet-example module worksheet-id return-path)))
        ;; Module not found
        (http-response/not-found (i18n/tr [:modules/no-module]))))))