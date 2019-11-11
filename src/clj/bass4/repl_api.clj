(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]))

(defn hash-password
  [password]
  (user-service/password-hasher password))

(defn x
  []
  (Thread/sleep 1000))