(ns bass4.routes.ext-login
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.services.bass :as bass]
            [mount.core :as mount]
            [clojure.pprint]
            [bass4.request-state :as request-state]
            [ring.util.codec :as codec]
            [clojure.tools.logging :as log]))

(defn check-ip
  [handler request]
  (let [{:keys [allowed ips]} (db/bool-cols db/ext-login-settings {} [:allowed])]
    (cond
      (not allowed) (layout/text-response "0 External login not allowed")
      :else (handler request))))

(defroutes ext-login-routes
  (context "/ext-login" [:as request]
    (GET "/checkpending" [] (layout/text-response "checkpending"))))