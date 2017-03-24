(ns bass4.responses.posts
  (:require [ring.util.http-response :as response]))

(defn ok [url]
  (response/ok "ok"))

(defn found [url]
  (response/ok (str "found " url)))

(defn re-auth []
  (response/ok "re-auth"))