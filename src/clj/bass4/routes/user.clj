(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.dashboard :as dashboard]
            [bass4.responses.user :as user-response]
            [bass4.services.treatment :as treatment-service]
            [bass4.responses.modules :as modules-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.assessments :as assessments-response]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [buddy.auth.accessrules :as buddy-rules]))

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

#_(defn module-routes
    [treatment-access render-map module]
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
        (modules-response/worksheet-example module worksheet-id return-path))))

#_(defn- messages-routes
    [user treatment render-map]
    (routes
      (GET "/messages" []
        (if (get-in treatment [:user-components :messaging])
          (messages-response/messages-page render-map user)
          (layout/error-404-page)))
      (POST "/messages" [& params]
        (if (get-in treatment [:user-components :send-messages])
          (messages-response/save-message (:user-id user) (:text params))
          (layout/error-404-page)))
      (POST "/message-save-draft" [& params]
        (if (get-in treatment [:user-components :send-messages])
          (messages-response/save-draft (:user-id user) (:text params))
          (layout/error-404-page)))
      (POST "/message-read" [& params]
        (messages-response/message-read (:user-id user) (:message-id params)))))


(defn no-treatment-response
  [session]
  (if (:assessments-performed? session)
    (->
      (http-response/found "/login")
      (assoc :session {}))
    (->
      (http-response/found "/no-activities")
      (assoc :session {}))))

#_(defn- treatment-routes
    [user request]
    (if-let [treatment (:db-treatment request)]
      (let [render-map       (:db-render-map request)
            treatment-access (:treatment-access treatment)]
        (routes
          (GET "/" []
            (dashboard/dashboard user (:session request) render-map treatment))
          ;; MESSAGES
          (GET "/messages" []
            (if (get-in treatment [:user-components :messaging])
              (messages-response/messages-page render-map user)
              (layout/error-404-page)))
          (POST "/messages" [& params]
            (if (get-in treatment [:user-components :send-messages])
              (messages-response/save-message (:user-id user) (:text params))
              (layout/error-404-page)))
          (POST "/message-save-draft" [& params]
            (if (get-in treatment [:user-components :send-messages])
              (messages-response/save-draft (:user-id user) (:text params))
              (layout/error-404-page)))
          (POST "/message-read" [& params]
            (messages-response/message-read (:user-id user) (:message-id params)))

          ;; MODULES
          (GET "/modules" []
            (modules-response/modules-list
              render-map
              (:modules (:user-components treatment))
              (get-in treatment [:treatment-access :treatment-access-id])))
          (context "/module/:module-id" [module-id]
            (if-let [module (->> (filter #(= (str->int module-id) (:module-id %)) (:modules (:user-components treatment)))
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
      (routes
        (ANY "*" [] (no-treatment-response (:session request))))))


(defn- eval-rules
  [request & rules]
  (let [db        (:db request)
        session   (:session request)
        treatment (:treatment db)]
    (let [res (loop [rules rules]
                (if (empty? rules)
                  true
                  (let [[pred pred-true pred-false] (first rules)
                        res (if (pred session treatment)
                              pred-true
                              pred-false)]
                    #_(log/debug (:uri request) "predicate returned" res)
                    (if (= :ok res)
                      (recur (rest rules))
                      res))))]
      (cond
        (true? res)
        true

        (string? res)
        (if (= :get (:request-method request))
          (buddy-rules/error (http-response/found res))
          (buddy-rules/error (layout/error-400-page)))

        (= 404 res)
        (buddy-rules/error (layout/error-404-page))

        (= 403 res)
        (buddy-rules/error (layout/error-403-page))

        :else
        (throw (Exception. "Rule did not return 404, true or string"))))))


;; ------------------------
;;          RULES
;; ------------------------

(defn- assessments-pending?
  [session _]
  (:assessments-pending? session))

(defn no-treatment-no-assessments?
  [session treatment]
  (and (not treatment) (not (:assessments-performed? session))))

(defn no-treatment-but-assessments?
  [session treatment]
  (and (not treatment) (:assessments-performed? session)))

(defn double-auth?
  [session _]
  (auth-response/need-double-auth? session))

(defn logged-in?
  [session _]
  (:identity session))

(def assessment-route-rules
  [{:pattern #"^/assessments"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? :ok "/user"]))}

   {:pattern #"^/user[/]?"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? "/assessments" :ok]
                                       [no-treatment-no-assessments? "/no-activities" :ok]
                                       [no-treatment-but-assessments? "/login" :ok]))}

   {:pattern #"^/user/.+"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? "/assessments" :ok]
                                       [no-treatment-no-assessments? "/no-activities" :ok]
                                       [no-treatment-but-assessments? "/login" :ok]))}])

(def user-route-rules
  [{:pattern #"^/assessments"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? :ok "/user"]))}

   {:pattern #"^/user[/]?"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? "/assessments" :ok]
                                       [no-treatment-no-assessments? "/no-activities" :ok]
                                       [no-treatment-but-assessments? "/login" :ok]))}

   {:pattern #"^/user/.+"
    :handler (fn [request] (eval-rules request
                                       [logged-in? :ok 403]
                                       [double-auth? "/double-auth" :ok]
                                       [assessments-pending? "/assessments" :ok]
                                       [no-treatment-no-assessments? "/no-activities" :ok]
                                       [no-treatment-but-assessments? "/login" :ok]))}])

(defroutes assessment-routes
  (context "/assessments" [:as
                           {{:keys [render-map treatment]}          :db
                            {:keys [user]}                          :session
                            {{:keys [treatment-access]} :treatment} :db
                            :as                                     request}]
    (GET "/" [] (assessments-response/handle-assessments (:user-id user) (:session request)))
    (POST "/" [instrument-id items specifications]
      (assessments-response/post-instrument-answers
        (:user-id user)
        (:session request)
        instrument-id
        items
        specifications))))

(defroutes user-routes
  (context "/user" [:as
                    {{:keys [render-map treatment]}          :db
                     {:keys [user]}                          :session
                     {{:keys [treatment-access]} :treatment} :db
                     :as                                     request}]
    (GET "/" []
      (dashboard/dashboard user (:session request) render-map treatment))
    ;; MESSAGES
    (GET "/messages" []
      (if (get-in treatment [:user-components :messaging])
        (messages-response/messages-page render-map user)
        (layout/error-404-page)))
    (POST "/messages" [& params]
      (if (get-in treatment [:user-components :send-messages])
        (messages-response/save-message (:user-id user) (:text params))
        (layout/error-404-page)))
    (POST "/message-save-draft" [& params]
      (if (get-in treatment [:user-components :send-messages])
        (messages-response/save-draft (:user-id user) (:text params))
        (layout/error-404-page)))
    (POST "/message-read" [& params]
      (messages-response/message-read (:user-id user) (:message-id params)))
    ;; MODULES
    (GET "/modules" []
      (modules-response/modules-list
        render-map
        (:modules (:user-components treatment))
        (get-in treatment [:treatment-access :treatment-access-id])))
    (context "/module/:module-id" [module-id]
      (if-let [module (->> (filter #(= (str->int module-id) (:module-id %)) (:modules (:user-components treatment)))
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


#_(def user-routes
    (context "/user" [:as request]
      (let [user (get-in request [:session :user])]
        (if (auth-response/need-double-auth? (:session request))
          (routes
            (ANY "*" [] (http-response/found "/double-auth")))
          (GET "/hejsan" [] (layout/text-response "hejsan"))
          #_(if (:assessments-pending? (:session request))
              (assessment-routes user request)
              (routes
                (GET "/hejsan" [] (layout/text-response "hejsan")))
              #_(treatment-routes user request))))))

#_(defroutes privacy-consent-routes
    (context "/user" [:as request]
      (let [user (get-in request [:session :user])]
        (GET "/privacy-consent" []
          (user-response/privacy-consent-page user))
        (POST "/privacy-consent" [i-consent :as request]
          (user-response/handle-privacy-consent user i-consent)))))

#_(defroutes testr
    (context "/" []
      (context "/xxx" [:as
                       {{:keys [render-map treatment]}          :db
                        {:keys [user]}                          :session
                        {{:keys [treatment-access]} :treatment} :db
                        :as                                     request}]
        (GET "/yyy" []
          (do (log/debug request)
              (log/debug treatment)
              (log/debug user)
              (layout/text-response treatment-access)))
        (GET "/xxx" []
          (layout/text-response render-map)))))