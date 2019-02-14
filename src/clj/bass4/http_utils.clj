(ns bass4.http-utils
  (:require [ring.util.http-response :as http-response]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (java.net URLEncoder)
           (java.util Formatter$DateTime)
           (java.io PrintWriter)))

(extend Formatter$DateTime json/JSONWriter {:-write (fn [x ^PrintWriter out]
                                                      (.print out (str x)))})
(defn get-ip
  [request]
  (-> (or (get-in request [:headers "x-forwarded-for"]) (:remote-addr request))
      (str/split #"[, ]")
      (first)))

(defn get-host
  [request]
  (let [headers (:headers request)]
    (get headers "x-forwarded-host" (get headers "host"))))

(defn get-server
  [request]
  (let [headers (:headers request)]
    (get headers "x-forwarded-host" (:server-name request))))

(defn get-host-address
  [request]
  (let [host   (get-host request)
        scheme (name (:scheme request))]
    (str scheme "://" host)))

(defn
  url-escape
  [s] (URLEncoder/encode s))

(defn json-response
  [x]
  (-> (http-response/ok (json/write-str x))
      (http-response/content-type "application/json")))