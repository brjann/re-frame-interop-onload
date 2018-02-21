(ns bass4.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [markdown.transformers :as md-transformers]
            [markdown.lists :refer :all]
            [bass4.config :refer [env]]
            [bass4.time :as b-time]
            [ring.util.http-response :refer [content-type ok]]
            [ring.util.response :refer [status] :as ring-response]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [bass4.i18n :as i18n]
            [clojure.string :refer [split join]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [bass4.services.bass :as bass-service]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [compojure.response :as response]
            [clojure.string :as string]))

(defn only-ul [text {:keys [code codeblock last-line-empty? eof lists] :as state}]
  (cond

    (and last-line-empty? (string/blank? text))
    [(str (close-lists (reverse lists)) text)
     (-> state (dissoc :lists) (assoc :last-line-empty? false))]

    (or code codeblock)
    (if (and lists (or last-line-empty? eof))
      [(str (close-lists (reverse lists)) text)
       (-> state (dissoc :lists) (assoc :last-line-empty? false))]
      [text state])

    (and (not eof)
         lists
         (string/blank? text))
    [text (assoc state :last-line-empty? true)]

    :else
    (let [indents  (if last-line-empty? 0 (count (take-while (partial = \space) text)))
          trimmed  (string/trim text)
          in-list? (:lists state)]
      (cond
        (re-find #"^[\*\+-] " trimmed)
        (ul (if in-list? text trimmed) state)

        #_(re-find #"^[0-9]+\. " trimmed)
        #_(ol (if in-list? text trimmed) state)

        (pos? indents)
        [text state]

        (and (or eof last-line-empty?)
             (not-empty lists))
        [(close-lists (reverse lists))
         (assoc state :lists [] :buf text)]

        :else
        [text state]))))

(defn remove-comments [text state]
  "Remove one-line html comments from markdown"
  [(clojure.string/replace text #"<!--(.*?)-->" "") state])

(def new-transformer-vector
  (let [li-index (first (keep-indexed #(when (= markdown.lists/li %2) %1) md-transformers/transformer-vector))]
    (-> md-transformers/transformer-vector
        (assoc li-index only-ul)
        (#(cons remove-comments %)))))

(declare ^:dynamic *app-context*)
(parser/set-resource-path! (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
#_(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content :replacement-transformers new-transformer-vector)]))

(defn render
  "renders the HTML template located relative to resources/templates"
  [template & [params]]
  (content-type
    (ok
      (parser/render-file
        template
        (assoc params
          :dev (env :dev)
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
  ([error-details] (error-page error-details "error.html"))
  ([error-details html]
   {:status  (:status error-details)
    :headers {"Content-Type" "text/html; charset=utf-8"}
    :body    (parser/render-file html error-details)}))

(defn error-422
  "https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
  422 Unprocessable Entity (WebDAV; RFC 4918)
  The request was well-formed but was unable to be followed due to semantic errors.

  Used to communicate back to form that there was something wrong with
  the posted data. For example erroneous username-password combination"
  ([] (error-422 ""))
  ([body]
   {:status  422
    :headers {}
    :body    body}))

(defn error-403-page
  ([] (error-403-page nil))
  ([user-id] (error-403-page user-id (i18n/tr [:error/not-authorized-longer])))
  ([user-id message]
   (error-page {:status  403
                :title   (i18n/tr [:error/not-authorized])
                :message message}
               (if user-id "error-back-or-login.html" "error-login.html"))))

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
                :message message}
               "error-back-or-login.html")))

(defn throw-400!
  ([] (throw-400! ""))
  ([message]
   (let [sep (if (zero? (count message)) "" " ")]
     (throw (ex-info (str "400" sep message) {})))))

#_(defn text-response
    [var]
    (ring-response/content-type (ring-response/response (with-out-str (clojure.pprint/pprint var))) "text/plain"))

(defn text-response
  [var]
  (-> (str var)
      (ring-response/response)
      (ring-response/content-type "text/plain")))

(defn print-var-response
  [var]
  (-> (clojure.pprint/pprint var)
      (with-out-str)
      (ring-response/response)
      (ring-response/content-type "text/plain")))


#_(defn datetime-str
    [val resource-id]
    (-> (i18n/tr [resource-id])
        (f/formatter (bass/time-zone))
        (f/unparse (tc/from-date val))))

(defn datetime-str
  [val resource-id]
  (-> (i18n/tr [resource-id])
      (f/formatter (bass/time-zone))
      (f/unparse val)))

(defn day-diff-str
  [day-diff]
  (cond
    (zero? day-diff) (i18n/tr [:date-time/today])
    (= 1 day-diff) (i18n/tr [:date-time/yesterday])
    (and (>= 7 day-diff) (< 0 day-diff)) (i18n/tr [:date-time/days-ago] [(str day-diff)])
    (= -1 day-diff) (i18n/tr [:date-time/tomorrow])
    (and (<= -7 day-diff) (> 0 day-diff)) (i18n/tr [:date-time/in-days] [(str (- day-diff))])))


(defn date-nice-str
  [datetime]
  (let [day-diff (-> datetime
                     (b-time/days-since-tz))
        day-str  (day-diff-str day-diff)]
    (or day-str (datetime-str datetime :date-time/date))))

;;-------------
;; PARSER TAGS
;;-------------

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
  :date
  (fn [val]
    (datetime-str val :date-time/date)))

(filters/add-filter!
  :datetime-ns
  (fn [val]
    (datetime-str val :date-time/datetime-ns)))

(filters/add-filter!
  :nice-datetime
  (fn [val]
    (str (date-nice-str val) " " (datetime-str val :date-time/time-ns))))

(filters/add-filter!
  :nice-date
  (fn [val]
    (str (date-nice-str val))))

(defn route-not-found
  "Replacement function for route/not-found, which messes with the translation,
  because it has not been loaded yet.
  Returns a route that always returns a 404 \"Not Found\" response with the
  error body."
  []
  (fn [request]
    (let [body (error-404-page)]
      (-> (response/render body request)
          (status 404)
          (cond-> (= (:request-method request) :head) (assoc :body nil))))))
