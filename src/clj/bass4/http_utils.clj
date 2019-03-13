(ns bass4.http-utils
  (:require [ring.util.http-response :as http-response]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [bass4.config :as config])
  (:import (java.net URLEncoder)
           (java.io PrintWriter)
           (org.joda.time DateTime)))

(extend DateTime json/JSONWriter {:-write (fn [x ^PrintWriter out]
                                            (.print out (str "\"" x "\"")))})

#_(extend-protocol cheshire.generate/JSONable
  DateTime
  (to-json [dt gen]
    (cheshire.generate/write-string gen (str dt))))

(defn- x-forwarded-index
  [x-forwarded index]
  (let [ips (->> (str/split x-forwarded #"[, ]")
                 (remove empty?)
                 (reverse)
                 (into []))]
    (get ips index)))

(defn get-client-ip
  [request]
  (if-let [x-forwarded (get-in request [:headers "x-forwarded-for"])]
    (x-forwarded-index x-forwarded (config/env :x-forwarded-for-index 0))
    (:remote-addr request)))

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

(defn ajax?
  [request]
  (let [requested-with (get (:headers request) "x-requested-with" "")]
    (= "xmlhttprequest" (str/lower-case requested-with))))