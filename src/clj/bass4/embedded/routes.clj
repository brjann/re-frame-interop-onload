(ns bass4.embedded.routes
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.instrument.preview :as instrument-preview]
            [bass4.responses.content-example :as content-example]
            [bass4.module.responses :as modules]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.admin-panel :as admin-panel]
            [ring.util.http-response :as http-response]
            [bass4.responses.pluggable-ui :as pluggable-ui]
            [bass4.http-utils :as h-utils]))

;; TODO: Wrap only user requests in timeout/re-auth
(def embedded-routes
  (context "/embedded" [:as request]
    (routes
      (GET "/pluggable-ui*" []
        (pluggable-ui/pluggable-ui request "/embedded/pluggable-ui"))
      (GET "/instrument/:instrument-id" [instrument-id]
        (instrument-preview/instrument-page instrument-id))
      (POST "/instrument/:instrument-id" [instrument-id & params]
        (instrument-preview/post-answers instrument-id (:items params) (:specifications params)))
      (GET "/instrument/:instrument-id/summary" [instrument-id]
        (instrument-preview/summary-page instrument-id))
      (GET "/content-example/:content-id" [content-id]
        (content-example/edit-example content-id))
      (POST "/content-example/:content-id" [content-id content-data]
        (modules/save-worksheet-example-data content-id content-data))
      (context "/iframe" []
        (routes
          (POST "/render" [text markdown]
            (layout/render "render.html"
                           {:text      text
                            :markdown? markdown}))
          ;; Dummy function to have something to serve after URL has been allowed
          (GET "/render" []
            (h-utils/text-response ""))
          (GET "/view-user-content/:treatment-access-id/:module-id/:content-id"
               [treatment-access-id module-id content-id]
            (modules/view-user-content treatment-access-id module-id content-id))))
      (context "/error" []
        (routes
          (GET "/re-auth" []
            (layout/text-response "Timeout. You need to re-authenticate in the admin interface."))

          (GET "/no-session" []
            (layout/text-response "No session. You do not have an active admin session. Maybe you have been logged out? Please login again in the admin interface."))))

      (context "/admin" []
        (routes
          (GET "/" []
            (http-response/found "states"))

          (GET "/states" []
            (admin-panel/states-page))
          (POST "/states" [state-name]
            (admin-panel/reset-state state-name))
          (POST "/cancel-lockdown" []
            (admin-panel/cancel-lockdown!))
          (POST "/lock-down" []
            (admin-panel/lock-down!)))))))