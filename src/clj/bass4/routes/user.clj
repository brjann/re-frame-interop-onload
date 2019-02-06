(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.dashboard :as dashboard]
            [bass4.responses.user :as user-response]
            [bass4.responses.modules :as modules-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.assessments :as assessments-response]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.route-rules :as route-rules]
            [bass4.middleware.core :as middleware]
            [bass4.services.privacy :as privacy-service]
            [bass4.responses.error-report :as error-report-response]))

(defn request-string
  "Return the request part of the request."
  [request]
  (str (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))

;; TODO: Weird stuff going on here. This has been fixed - no?
;; The request is identified as http by (request/request-url request)
;; On the server with reverse proxy, some type of rewrite causes the url
;; to be double encoded. This is a hack until the problem can be solved
;; in Apache.
;(defn url-encode-x
;  "Return the request part of the request."
;  [string]
;  (if (env :no-url-encode)
;    string
;    (codec/url-encode string)))


(defn no-treatment-response
  [session]
  (if (:assessments-performed? session)
    (->
      (http-response/found "/login")
      (assoc :session {}))
    (->
      (http-response/found "/no-activities")
      (assoc :session {}))))

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
  (get-in treatment [:user-components :messaging]))

(defn send-messages?
  [{{:keys [treatment]} :db :as request} _]
  (if (= :post (:request-method request))
    (get-in treatment [:user-components :send-messages])
    true))

(defn limited-access?
  [{:keys [session]} _]
  (:limited-access? session))


(def user-route-rules
  [{:uri   "/user*"
    :rules [[#'consent-needed? "/privacy/consent" :ok]
            [#'assessments-pending? "/assessments" :ok]
            [#'no-treatment-no-assessments? "/no-activities" :ok]
            [#'no-treatment-but-assessments? "/login" :ok]
            [#'limited-access? "/escalate" :ok]]}
   {:uri   "/user/message*"
    :rules [[#'messages? :ok 404]
            [#'send-messages? :ok 404]]}])

(def privacy-consent-rules
  [{:uri   "/privacy/consent"
    :rules [[#'consent-needed? :ok "/user"]]}])

(def assessment-route-rules
  [{:uri   "/assessments*"
    :rules [[#'consent-needed? "/privacy/consent" :ok]]}])

(defn user-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/user*"]
    (route-rules/wrap-rules user-route-rules)
    #'user-response/treatment-mw                            ; Adds treatment info to request
    #'user-response/check-assessments-mw
    #'auth-response/auth-re-auth-mw
    #'middleware/wrap-csrf
    #'auth-response/double-auth-mw
    #'auth-response/restricted-mw))

(defn privacy-consent-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/privacy/*"]
    (route-rules/wrap-rules privacy-consent-rules)
    #'auth-response/auth-re-auth-mw
    #'middleware/wrap-csrf
    #'auth-response/double-auth-mw
    #'auth-response/restricted-mw))

(defn ajax-user-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/ajax-user/*"]
    #'user-response/check-assessments-mw
    #_#'auth-response/auth-re-auth-mw
    #'middleware/wrap-csrf
    #_#'auth-response/double-auth-mw
    #'auth-response/restricted-mw))

(defn assessment-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/assessments*"]
    (route-rules/wrap-rules assessment-route-rules)
    #'user-response/check-assessments-mw
    #'auth-response/auth-re-auth-mw
    #'middleware/wrap-csrf
    #'auth-response/double-auth-mw
    #'auth-response/restricted-mw))

(defroutes assessment-routes
  (context "/assessments" [:as {{:keys [user]} :db
                                :as            request}]
    (GET "/" [] (assessments-response/handle-assessments (:user-id user) (:session request)))
    (POST "/" [instrument-id items specifications]
      (assessments-response/post-instrument-answers
        (:user-id user)
        (:session request)
        instrument-id
        items
        specifications))))

(defroutes ajax-user-routes
  (context "/ajax-user" [:as
                         {{:keys [user]} :db
                          :as            request}]
    (GET "/privacy-notice" []
      (user-response/privacy-notice-bare user))))

(defroutes privacy-consent-routes
  (context "/privacy" [:as
                       {{:keys [user]}                          :db
                        {{:keys [treatment-access]} :treatment} :db
                        :as                                     request}]
    (GET "/consent" []
      (user-response/privacy-consent-page user))
    (POST "/consent" [i-consent]
      (user-response/handle-privacy-consent user i-consent))))

(defroutes user-routes
  (context "/user" [:as
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