(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
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
(defn url-encode-x
  "Return the request part of the request."
  [string]
  (if (env :no-url-encode)
    string
    (codec/url-encode string)))


(defn module-routes
  [treatment-access render-fn module]
  (routes
    (GET "/" [] (modules-response/main-text treatment-access render-fn module))
    (GET "/homework" [] (modules-response/homework treatment-access render-fn module))
    (GET "/worksheet/:worksheet-id" [worksheet-id] (modules-response/worksheet treatment-access render-fn module (str->int worksheet-id)))))

(defn treatment-routes
  [user request]
  (let [treatment (treatment-service/user-treatment (:user-id user))
        render-fn (user-response/user-page-renderer treatment (:uri request))]
    ;; TODO: Check if actually in treatment
    (routes
      (GET "/" [] "this is the dashboard")

      ;; MESSAGES
      (GET "/messages" []
        (messages-response/messages-page render-fn user))
      (POST "/messages" [& params]
        (messages-response/save-message (:user-id user) (:subject params) (:text params)))
      (POST "/message-save-draft" [& params]
        (messages-response/save-draft (:user-id user) (:subject params) (:text params)))

      ;; MODULES
      (GET "/modules" []
        (modules-response/modules-list render-fn (:modules (:user-components treatment))))
      (context "/module/:module-id" [module-id]
        (if-let [module (->> (filter #(= (str->int module-id) (:module-id %)) (:modules (:user-components treatment)))
                             (some #(and (:active %) %)))]
          (module-routes (:treatment-access treatment) render-fn module)
          ;; Module not found
          (layout/error-404-page (i18n/tr [:modules/no-module]))))
      (POST "/content-data" [& params]
        (let [treatment-access-id (:treatment-access-id (:treatment-access treatment))
              data-map            (into [] (json-safe (:content-data params)))]
          (log/debug data-map)
          (content-data-service/save-content-data! data-map treatment-access-id))
        (response/found "reload"))

      #_(GET "/" req (dashboard-page req))
      #_(GET "/profile" [errors :as req] (profile-page req errors))
      #_(GET "/worksheets" [worksheet-id :as req] (worksheets-page worksheet-id req))
      #_(POST "/worksheets" [& params :as req] (handle-worksheet-submit params req))
      #_(GET "/charts" req (charts-page req)))))

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
      (if (not (get-in request [:session :auth-timeout]))
        (if (auth-response/double-authed? (:session request))
          (if (:assessments-pending (:session request))
            (assessment-routes user request)
            (treatment-routes user request))
          (routes
            (ANY "*" [] (response/found "/double-auth"))))
        (routes
          (GET "*" [:as request]
            (response/found (str "/re-auth?return-url=" (url-encode-x (request-string request)))))
          (POST "*" [] (auth-response/re-auth-440)))))))


