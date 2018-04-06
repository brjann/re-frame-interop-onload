(ns bass4.http-utils
  (:import (java.net URLEncoder)))

(defn get-ip
  [request]
  (or (get-in request [:headers "x-forwarded-for"]) (:remote-addr request)))

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