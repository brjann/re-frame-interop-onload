(ns bass4.responses.treatment
  (:require [ring.util.http-response :as http-response]
            [schema.core :as s]
            [ring.middleware.anti-forgery :as anti-forgery]
            [bass4.utils :refer [json-safe]]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.services.treatment :as treatment-service]
            [clojure.tools.logging :as log])
  (:import (org.joda.time DateTime)))


; -----------------------
;       MIDDLEWARE
; -----------------------

(defn treatment-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))

(defn treatment-mw
  [handler]
  (fn [request]
    (if-let [treatment (when-let [user (get-in request [:db :user])]
                         (treatment-service/user-treatment (:user-id user)))]
      (handler (-> request
                   (assoc-in [:db :treatment] treatment)
                   (assoc-in [:db :render-map] (treatment-page-map treatment (:uri request)))))
      (handler request))))


; -----------------------
;          API
; -----------------------

(defn csrf
  []
  (let [x (force anti-forgery/*anti-forgery-token*)]
    (if (string? x)
      x
      "")))

(s/defschema Module-info
  {:module-id       s/Int
   :module-name     String
   :active          Boolean
   :activation-date (s/maybe DateTime)
   :homework-status (s/maybe (s/enum :ok :submitted))
   :tags            [String]})

(s/defschema Treatment-info
  {:last-login-time (s/maybe DateTime)
   :start-date      (s/maybe DateTime)
   :end-date        (s/maybe DateTime)
   :modules         [Module-info]
   :new-messages?   Boolean
   :messaging?      Boolean
   :send-messages?  Boolean})

(defapi api-tx-info
  [user :- map? treatment :- map?]
  (log/debug (keys (:user-components treatment)))
  (let [res (merge
              (select-keys user [:last-login-time])
              (select-keys (:treatment-access treatment) [:start-date :end-date])
              (select-keys treatment [:new-messages?])
              (select-keys (:user-components treatment) [:messaging? :send-messages?])
              {:modules (mapv #(select-keys % [:module-id
                                               :module-name
                                               :active
                                               :activation-date
                                               :homework-status
                                               :tags])
                              (get-in treatment [:user-components :modules]))})]
    (http-response/ok res)))



