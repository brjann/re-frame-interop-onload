(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.layout :as layout]))



(defn modules-list [user modules]
  ["modules-list.html"
   {:modules modules}])