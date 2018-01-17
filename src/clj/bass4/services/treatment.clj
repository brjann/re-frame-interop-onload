(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [clj-time.coerce :as c]
            [bass4.time :as b-time]
            [clojure.set]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool boolean? fnil+]]
            [bass4.services.messages :as messages]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Does probably not handle automatic module accesses

(defn- submitted-homeworks
  [treatment-access]
  (->> (db/get-submitted-homeworks {:treatment-access-id (:treatment-access-id treatment-access)})
       (group-by :module-id)
       (map-map first)
       (map-map #(assoc % :ok (= 1 (:ok %))))))

(defn- user-treatment-accesses
  [user-id]
  (let [active-modules #(into #{} (keys (filter-map identity (map-map val-to-bool %))))]
    (mapv (fn [treatment-access]
            (-> treatment-access
                (unserialize-key :module-accesses)
                (#(assoc % :modules-active (active-modules (:module-accesses %))))
                (#(assoc % :modules-activation-timestamp (filter-map (complement zero?) (:module-accesses %))))
                (#(assoc % :submitted-homeworks (submitted-homeworks %)))
                (dissoc :module-accesses)))
          (db/bool-cols
            db/get-treatment-accesses
            {:user-id user-id}
            [:access-enabled
             :messages-send-allowed
             :messages-receive-allowed]))))

(defn- categorize-module-contents
  [contents]
  (let [categorized (group-by :type contents)]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     :main-texts (get categorized "MainTexts")}))


(defn get-content
  [content-id]
  (-> (db/bool-cols
        db/get-content
        {:content-id content-id}
        [:markdown :tabbed :show-example])
      (unserialize-key :data-imports)
      ;; Transform true false array for imports into list of imported data
      (#(assoc % :data-imports (keys (filter-map identity (:data-imports %)))))))

(defn get-module-contents
  [module-id]
  (let [contents (db/get-module-contents {:module-ids [module-id]})]
    (categorize-module-contents contents)))

(defn get-multiple-module-contents
  [module-ids]
  (let [contents (db/get-module-contents {:module-ids module-ids})]
    (map-map categorize-module-contents (group-by :module-id contents))))

(defn treatment-map
  [treatment-id]
  (let [info    (-> (db/bool-cols
                      db/get-treatment-info
                      {:treatment-id treatment-id}
                      [:access-time-limited
                       :access-enabling-required
                       :modules-manual-access
                       :module-automatic-access
                       :messages-send-allowed
                       :messages-receive-allowed])
                    (unserialize-key
                      :modules-automatic-access
                      #(into #{} (keys (filter-map identity (map-map val-to-bool %))))))
        modules (db/get-treatment-modules {:treatment-id treatment-id})]
    (merge info
           {:modules modules})))


(defn- convert-dates
  [treatment-access]
  [(clj-time.coerce/from-sql-date (:start-date treatment-access))
   (clj-time.coerce/from-sql-date (:end-date treatment-access))])

(defn- treatment-ongoing?
  [treatment-access]
  (let [[start-date end-date] (convert-dates treatment-access)]
    (and (t/before? start-date (t/now))
         (t/after? end-date (t/now)))))

(defn treatment-active?
  [treatment-access treatment]
  (if (:access-time-limited treatment)
    (treatment-ongoing? treatment-access)
    (if (:access-enabling-required treatment)
      (:access-enabled treatment-access)
      true)))

(defn modules-component
  [treatment-access treatment]
  (let [active-fn          #(or (not (:modules-manual-access treatment))
                                (contains?
                                  (clojure.set/union
                                    (:modules-active treatment-access)
                                    (:modules-automatic-access treatment))
                                  (:module-id %)))
        activation-date-fn #(when (and (:modules-manual-access treatment))
                              (fnil+ b-time/from-unix
                                     (get-in treatment-access
                                             [:modules-activation-timestamp (:module-id %)])))]
    (map #(merge %
                 {:active          (active-fn %)
                  :activation-date (activation-date-fn %)})
         (:modules treatment))))

(defn user-components
  [treatment-access treatment]
  {:modules       (modules-component treatment-access treatment)
   :messaging     true
   :send-messages (true? (and (:messages-send-allowed treatment) (:messages-send-allowed treatment-access)))})


;; TODO: BulletinBoard!?
(defn user-treatment
  [user-id]
  ;; For now, only the first active treatment access is considered.
  ;; If multiple treatments are available, the session needs to keep track
  ;; of the cTreatmentAccess content in which treatment content is shown.
  ;; Either in the URL or in a state. Too early to decide now - use case
  ;; has never surfaced.
  (when-let [[treatment-access treatment] (->> (map
                                                 #(vector % (treatment-map (:treatment-id %)))
                                                 (user-treatment-accesses user-id))
                                               (some #(when (apply treatment-active? %) %)))]
    (let [treatment (treatment-map (:treatment-id treatment-access))]
      {:treatment-access treatment-access
       :new-messages?    (messages/new-messages? user-id)
       :user-components  (user-components treatment-access treatment)
       :treatment        treatment})))

(defn submit-homework!
  [treatment-access module]
  (db/submit-homework! {:treatment-access-id (:treatment-access-id treatment-access)
                        :module-id           (:module-id module)}))

(defn retract-homework!
  [submitted module]
  (db/retract-homework! {:submit-id (:submit-id submitted)}))