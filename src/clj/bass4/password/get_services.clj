(ns bass4.password.get-services
  (:require [bass4.passwords :as passwords]))

(defn gen-uid
  []
  (passwords/letters-digits 13 passwords/url-safe-chars))