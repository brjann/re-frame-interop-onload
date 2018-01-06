(ns bass4.routes.embedded
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.responses.content-example :as content-example]
            [bass4.responses.modules :as modules]
            [bass4.utils :refer [str->int json-safe]]
            [bass4.services.bass :as bass]))


;; TODO: Wrap only user requests in timeout/re-auth
(def embedded-routes
  (context "/embedded" [:as request]
    (routes
      (GET "/instrument/:instrument-id" [instrument-id]
        (instruments/instrument-page instrument-id))
      (POST "/instrument/:instrument-id" [instrument-id & params]
        (instruments/post-answers (str->int instrument-id) (:items params) (:specifications params)))
      (GET "/instrument/summary/:instrument-id" [instrument-id]
        (instruments/summary-page instrument-id))
      (GET "/content-example/:content-id" [content-id]
        (content-example/edit-example (str->int content-id)))
      (POST "/content-example/:content-id" [content-id & params]
        (modules/save-worksheet-data content-id (json-safe (:content-data params)))))))