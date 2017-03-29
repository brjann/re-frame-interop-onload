(ns bass4.views.auth
  (:require [bass4.layout :as layout]))

(defn double-auth [double-auth-code]
  (layout/render
    "double-auth.html"
    {:double-auth-code double-auth-code}))

(defn login-page []
  (layout/render
    "login.html"))

(defn re-auth-page
  ([return-url] (re-auth-page return-url false))
  ([return-url error] (layout/render
     "re-auth.html"
     {:return-url return-url
      :error error})))