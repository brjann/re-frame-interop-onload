(ns bass4.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [markdown.transformers :as md-transformers]
            [markdown.lists :refer :all]
            [clojure.data.json :as json]
            [bass4.config :refer [env]]
            [bass4.time :as b-time]
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
            [clojure.string :as string]
            [clojure.java.io :as io]
            [ring.util.http-response :refer [content-type ok] :as http-response]
            [clj-time.coerce :as tc]
            [selmer.tags :as tags]
            [bass4.services.privacy :as privacy-service]
            [bass4.session.timeout :as session-timeout]
            [bass4.middleware.embedded :as embedded-mw]))

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

(parser/set-resource-path! (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

;; Remove \u2028 and \u2029 (line endings) to prevent EOF in Javascript on iOS 11
;; https://stackoverflow.com/questions/2965293/javascript-parse-error-on-u2028-unicode-character
(filters/add-filter! :json (fn [x] (-> x
                                       (json/write-str)
                                       (string/replace #"[\u2028\u2029]" ""))))

#_(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content :replacement-transformers new-transformer-vector)]))

(defn render
  "renders the HTML template located relative to resources/templates"
  [template & [params]]
  (let [csrf-token (if (bound? #'*anti-forgery-token*)
                     (force *anti-forgery-token*)
                     "")]
    (content-type
      (ok
        (parser/render-file
          template
          (merge {:in-session?              (and session-timeout/*in-session?*
                                                 (not embedded-mw/*embedded-request?*))
                  :dev                      (env :dev)
                  :dev?                     (env :dev)
                  :privacy-notice-disabled? (privacy-service/privacy-notice-disabled?)
                  :page                     template
                  :csrf-token               csrf-token
                  :timeout-hard-soon        (session-timeout/timeout-hard-soon-limit)
                  :title                    (bass-service/db-title)}
                 params)))
      "text/html; charset=utf-8")))

(defn text-response
  [var]
  (-> (str var)
      (http-response/ok)
      (http-response/content-type "text/plain")))

(defn print-var-response
  [var]
  (-> (clojure.pprint/pprint var)
      (with-out-str)
      (http-response/ok)
      (http-response/content-type "text/plain")))


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

(def resource-mtimes (atom {}))

(defn get-resource-mtime
  [path]
  (if-not (contains? @resource-mtimes path)
    (let [url   (io/resource (str "public" path))
          data  (http-response/resource-data url)
          mtime (b-time/to-unix (tc/from-date (:last-modified data)))]
      (swap! resource-mtimes #(assoc % path mtime))))
  (get @resource-mtimes path))

(parser/add-tag!
  :script-modified
  (fn [[path & args] context-map]
    (let [len   (count path)
          path  (subs path 1 (- len 1))
          mtime (get-resource-mtime path)]
      ((tags/script-handler (cons (str "\"" path "?" mtime "\"") args) nil nil nil) context-map))))

(parser/add-tag!
  :style-modified
  (fn [[path & args] context-map]
    (let [len   (count path)
          path  (subs path 1 (- len 1))
          mtime (get-resource-mtime path)]
      ((tags/style-handler (cons (str "\"" path "?" mtime "\"") args) nil nil nil) context-map))))

(parser/add-tag!
  :trb
  (fn [args context-map content]
    (i18n/tr [(keyword (first args))]
             (split (get-in content [:trb :content]) #"[|]")))
  :endtrb)

(parser/add-tag!
  :tra
  (fn [args context-map content]
    (i18n/tr [(keyword (get-in content [:tra :content]))]))
  :endtra)

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
