(ns bass4.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [ring.util.http-response :refer [content-type ok]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [bass4.i18n :as i18n]
            [clojure.string :refer [split join]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

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
          :servlet-context *app-context*)))
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

(parser/add-tag! :tr
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
    (f/unparse (f/formatter (i18n/tr [:date-time/datetime-ns])) (c/from-date val))))