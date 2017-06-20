(ns bass4.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [ring.util.http-response :refer [content-type ok]]
            [ring.util.response :refer [status]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [bass4.i18n :as i18n]
            [clojure.string :refer [split join]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [bass4.services.bass :as bass-service]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [compojure.response :as response]))

(declare ^:dynamic *app-context*)
(parser/set-resource-path!  (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

(defn render
  "renders the HTML template located relative to resources/templates"
  [template & [params]]
  (content-type
    (ok
      (parser/render-file
        template
        (assoc params
          :page template
          :csrf-token *anti-forgery-token*
          :servlet-context *app-context*
          :title (bass-service/db-title))))
    "text/html; charset=utf-8"))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})

(defn error-400-page
  ([] (error-400-page nil))
  ([message]
   (error-page {:status  400
                       :title   "Bad request!"
                       :message message})))

(defn error-404-page
  ([] (error-404-page (i18n/tr [:error/page-not-found-longer])))
  ([message]
   (error-page {:status  404
                :title   (i18n/tr [:error/page-not-found])
                :message message})))

(parser/add-tag!
  :tr
  (fn [args context-map]
    (i18n/tr [(keyword (first args))]
             (split (join " " (rest args)) #"[|]"))))

(parser/add-tag!
  :trb
  (fn [args context-map content]
    (i18n/tr [(keyword (first args))]
             (split (get-in content [:trb :content]) #"[|]")))
  :endtrb)

(filters/add-filter!
  :datetime-ns
  (fn [val]
    (f/unparse (f/with-zone (f/formatter (i18n/tr [:date-time/datetime-ns])) (bass/time-zone)) (tc/from-date val))))

(defn route-not-found
  "Replacement function for route/not-found, which messes with the translation,
  because it has not been loaded yet.
  Returns a route that always returns a 404 \"Not Found\" response with the
  error body."
  []
  (fn [request]
    (let [body #_(:body (error-page {:status 404
                                   :title  "page not found"}))
          (error-404-page)]
      (-> (response/render body request)
          (status 404)
          (cond-> (= (:request-method request) :head) (assoc :body nil))))))