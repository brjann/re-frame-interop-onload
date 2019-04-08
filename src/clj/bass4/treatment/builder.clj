(ns bass4.treatment.builder
  (:require [clj-time.core :as t]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.services.messages :as messages]
            [bass4.module.services :as module-service]
            [bass4.treatment.services :as treatment-service]))


;; --------------------------
;;    TREATMENT RETRIEVAL
;; --------------------------

(defn- treatment-map
  [treatment-id]
  (let [info    (treatment-service/treatment-info treatment-id)
        modules (module-service/get-treatment-modules treatment-id)]
    (merge info
           {:modules modules})))

(defn modules-component
  [treatment-access treatment]
  (let [active-fn          #(or (not (:modules-manual-access? treatment))
                                (contains?
                                  (clojure.set/union
                                    (:modules-active treatment-access)
                                    (:modules-automatic-access treatment))
                                  (:module-id %)))
        activation-date-fn #(when (and (:modules-manual-access? treatment))
                              (get-in treatment-access
                                      [:modules-activation-dates (:module-id %)]))
        homework-status-fn #(case (get-in treatment-access
                                          [:submitted-homeworks (:module-id %) :ok?])
                              nil nil
                              true :ok
                              false :submitted)]
    (map #(merge %
                 {:active?         (active-fn %)
                  :activation-date (activation-date-fn %)
                  :homework-status (homework-status-fn %)})
         (:modules treatment))))

;; --------------------------
;; TREATMENT ACCESS RETRIEVAL
;; --------------------------

(defn- treatment-ongoing?
  [treatment-access]
  (let [[start-date end-date] [(:start-date treatment-access) (:end-date treatment-access)]]
    (and (t/before? start-date (t/now))
         (t/after? (t/plus end-date (t/days 1)) (t/now)))))

(defn treatment-active?
  [treatment-access treatment]
  (if (:access-time-limited? treatment)
    (treatment-ongoing? treatment-access)
    (if (:access-enabling-required? treatment)
      (:access-enabled? treatment-access)
      true)))

(defn- tx-components
  [treatment-access treatment]
  {:modules        (modules-component treatment-access treatment)
   :messaging?     true
   :send-messages? (true? (and (:messages-send-allowed? treatment) (:messages-send-allowed? treatment-access)))})


;; TODO: BulletinBoard!?
(defn user-treatment
  [user-id]
  ;; For now, only the first active treatment access is considered.
  ;; If multiple treatments are available, the session needs to keep track
  ;; of the cTreatmentAccess content in which treatment content is shown.
  ;; Either in the URL or in a state. Too early to decide now - use case
  ;; has never surfaced.
  (when-let [[treatment-access _] (->> (map
                                         #(vector % (treatment-map (:treatment-id %)))
                                         (treatment-service/user-treatment-accesses user-id))
                                       (some #(when (apply treatment-active? %) %)))]
    (let [treatment (treatment-map (:treatment-id treatment-access))]
      {:treatment-access treatment-access
       :new-messages?    (messages/new-messages? user-id)
       :tx-components    (tx-components treatment-access treatment)
       :treatment        treatment})))