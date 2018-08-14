(ns bass4.responses.user
  (:require [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.privacy :as privacy-service]
            [ring.util.http-response :as http-response]))


(defn user-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))


(defn- must-consent?
  [request]
  (when (= :get (:request-method request))
    (when-let [user (get-in request [:session :user])]
      (if-not (:privacy-notice-consent-time user)
        (privacy-service/user-must-consent? (:project-id user))))))

(defn privacy-notice-mw
  [handler request]
  (if (must-consent? request)
    (http-response/found "https://www.dn.se")
    (handler request)))