(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.dashboard :as dashboard]
            [bass4.responses.user :as user-response]
            [bass4.services.user :as user-service]
            [bass4.services.content-data :as content-data-service]
            [bass4.services.treatment :as treatment-service]
            [bass4.responses.modules :as modules-response]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.assessments :as assessments-response]
            [ring.util.http-response :as response]
            [ring.util.request :as request]
            [ring.util.codec :as codec]
            [bass4.request-state :as request-state]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

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

(defn module-routes
  [treatment-access render-map module]
  (routes
    (GET "/" [] (modules-response/main-text treatment-access render-map module))
    (POST "/" [& params]
      (modules-response/save-main-text-data
        treatment-access
        (json-safe (:content-data params))))
    (GET "/homework" [] (modules-response/homework treatment-access render-map module))
    (POST "/homework" [& params]
      (modules-response/save-homework
        treatment-access
        module
        (json-safe (:content-data params))
        (= 1 (str->int (:submitting params)))))
    (POST "/retract-homework" []
      (modules-response/retract-homework
        treatment-access
        module))
    (GET "/worksheet/:worksheet-id" [worksheet-id]
      (modules-response/worksheet treatment-access render-map module (str->int worksheet-id)))
    (GET "/worksheet/:worksheet-id/example" [worksheet-id & params]
      (modules-response/worksheet-example module (str->int worksheet-id) (:return params)))))

(defn- messages-routes
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
      (messages-response/message-read (:user-id user) (str->int (:message-id params))))))

(defn- treatment-routes
  [user request]
  (if-let [treatment (treatment-service/user-treatment (:user-id user))]
    (let [render-map (user-response/user-page-map treatment (:uri request))]
      (routes
        (GET "/" []
          (dashboard/dashboard user (:session request) render-map treatment))
        ;; MESSAGES
        (messages-routes user treatment render-map)

        ;; MODULES
        (GET "/modules" []
          (modules-response/modules-list
            render-map
            (:modules (:user-components treatment))
            (get-in treatment [:treatment-access :treatment-access-id])))
        (context "/module/:module-id" [module-id]
          (if-let [module (->> (filter #(= (str->int module-id) (:module-id %)) (:modules (:user-components treatment)))
                               (some #(and (:active %) %)))]
            (module-routes (:treatment-access treatment) render-map module)
            ;; Module not found
            (layout/error-404-page (i18n/tr [:modules/no-module]))))
        (POST "/content-data" [& params]
          (modules-response/save-worksheet-data
            (get-in treatment [:treatment-access :treatment-access-id])
            (json-safe (:content-data params))))))
    (routes
      ;; TODO: What should be shown if not in treatment?
      (ANY "*" [] (layout/text-response "Empty page")))))


(defn assessment-routes
  [user request]
  (routes
    (GET "*" [] (assessments-response/handle-assessments (:user-id user) (:session request)))
    (POST "*" [& params] (assessments-response/post-instrument-answers
                           (:user-id user)
                           (:session request)
                           (str->int (:instrument-id params))
                           (:items params)
                           (:specifications params)))))

(def user-routes
  (context "/user" [:as request]
    (let [user (get-in request [:session :user])]
      (if (auth-response/double-authed? (:session request))
        (if (:assessments-pending (:session request))
          (assessment-routes user request)
          (treatment-routes user request))
        (routes
          (ANY "*" [] (response/found "/double-auth")))))))