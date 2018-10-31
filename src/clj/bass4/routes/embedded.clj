(ns bass4.routes.embedded
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.responses.instrument :as instruments]
            [bass4.responses.content-example :as content-example]
            [bass4.responses.modules :as modules]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.responses.admin-panel :as admin-panel]
            [ring.util.http-response :as http-response]))


;; TODO: Wrap only user requests in timeout/re-auth
(def embedded-routes
  (context "/embedded" [:as request]
    (routes
      (GET "/instrument/:instrument-id" [instrument-id]
        (instruments/instrument-page instrument-id))
      (POST "/instrument/:instrument-id" [instrument-id & params]
        (instruments/post-answers instrument-id (:items params) (:specifications params)))
      (GET "/instrument/:instrument-id/summary" [instrument-id]

        (instruments/summary-page instrument-id))
      (GET "/content-example/:content-id" [content-id]
        (content-example/edit-example content-id))
      (POST "/content-example/:content-id" [content-id content-data]
        (modules/save-worksheet-example-data content-id content-data))

      (POST "/render" [text markdown]
        (layout/render "render.html"
                       {:text     text
                        :markdown markdown}))

      (context "/admin" []
        (routes
          (GET "/" []
            (http-response/found "states"))

          (GET "/states" []
            (admin-panel/states-page))
          (POST "/states" [state-name]
            (admin-panel/reset-state state-name)))))))