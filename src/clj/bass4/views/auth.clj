(ns bass4.views.auth
  (:require [bass4.layout :as layout]))

(defn double-auth [double-auth-code]
  (layout/render
    "double-auth.html"
    {:double-auth-code double-auth-code}))