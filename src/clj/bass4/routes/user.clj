(ns bass4.routes.user
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.responses.messages :as messages-response]
            [bass4.responses.user :as user-response]
            [bass4.services.user :as user-service]
            [bass4.services.treatment :as treatment-service]
            [bass4.config :refer [env]]
            [bass4.utils :refer [str->int]]
            [bass4.responses.auth :as auth-response]
            [bass4.responses.assessments :as assessments-response]
            [ring.util.http-response :as response]
            [ring.util.request :as request]
            [ring.util.codec :as codec]
            [bass4.request-state :as request-state]))

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



(defn treatment-routes
  [user]
  (let [treatments (treatment-service/user-treatments (:user-id user))]
    ;; TODO: Check if actually in treatment
    (routes
      (GET "/" [] "this is the dashboard")
      (GET "/messages" []
        (let [[template params] (messages-response/messages-page user)]
          (user-response/render-user-page treatments template params)))
      (POST "/messages" [& params]
        (messages-response/save-message (:user-id user) (:subject params) (:text params)))
      (POST "/message-save-draft" [& params]
        (messages-response/save-draft (:user-id user) (:subject params) (:text params)))
      #_(GET "/" req (dashboard-page req))
      #_(GET "/profile" [errors :as req] (profile-page req errors))
      #_(GET "/modules" req (modules-page req))
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
            (treatment-routes user))
          (routes
            (ANY "*" [] (response/found "/double-auth"))))
        (routes
          (GET "*" [:as request]
            (response/found (str "/re-auth?return-url=" (url-encode-x (request-string request)))))
          (POST "*" [] (auth-response/re-auth-440)))))))


