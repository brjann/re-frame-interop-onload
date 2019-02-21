(ns bass4.responses.user
  (:require [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.privacy :as privacy-service]
            [ring.util.http-response :as http-response]
            [markdown.core :as md]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.layout :as layout]
            [bass4.services.user :as user-service]
            [clj-time.core :as t]
            [bass4.services.assessments :as administrations]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.bass :as bass]
            [bass4.i18n :as i18n]
            [schema.core :as s]
            [ring.middleware.anti-forgery :as anti-forgery])
  (:import (org.joda.time DateTime)))

(defn user-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))

(s/defschema Module-info
  {:module-id       s/Int
   :module-name     String
   :active          Boolean
   :activation-date (s/maybe DateTime)
   :homework-status (s/maybe (s/enum :ok :submitted))
   :tags            [String]})

(s/defschema Treatment-info
  {:csrf            String
   :last-login-time (s/maybe DateTime)
   :start-date      (s/maybe DateTime)
   :end-date        (s/maybe DateTime)
   :modules         [Module-info]
   :new-messages?   Boolean
   :messaging?      Boolean
   :send-messages?  Boolean})

(defn csrf
  []
  (let [x (force anti-forgery/*anti-forgery-token*)]
    (if (string? x)
      x
      "")))

(defapi api-tx-info
  [user :- map? treatment :- map?]
  (let [res (merge
              {:csrf (csrf)}
              (select-keys user [:last-login-time])
              (select-keys (:treatment-access treatment) [:start-date :end-date])
              (select-keys treatment [:new-messages?])
              (:user-components treatment))]
    (http-response/ok res)))

(defn treatment-mw
  [handler]
  (fn [request]
    (if-let [treatment (when-let [user (get-in request [:db :user])]
                         (treatment-service/user-treatment (:user-id user)))]
      (handler (-> request
                   (assoc-in [:db :treatment] treatment)
                   (assoc-in [:db :render-map] (user-page-map treatment (:uri request)))))
      (handler request))))



