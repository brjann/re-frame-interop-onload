(ns bass4.views.auth
  (:require [bass4.layout :as layout]))

(defn double-auth [double-auth-code]
  (layout/render
    "double-auth.html"
    {:double-auth-code double-auth-code}))

(defn login-page
  ([] (login-page false))
  ([error]
    (layout/render
      "login.html"
      {:error error})))

(defn re-auth-page [return-url]
  (layout/render
    "re-auth.html"
    {:return-url return-url}))