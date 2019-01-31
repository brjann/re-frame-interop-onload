(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [clj-time.coerce :as tc]
            [bass4.time :as b-time]
            [clojure.set]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.services.messages :as messages]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Does probably not handle automatic module accesses

(defn- submitted-homeworks
  [treatment-access]
  (->> (db/get-submitted-homeworks {:treatment-access-id (:treatment-access-id treatment-access)})
       (group-by :module-id)
       (map-map first)
       #_(map-map #(assoc % :ok (= 1 (:ok %))))))

(defn- user-treatment-accesses
  [user-id]
  ;; Evidently, the access dates can be strings when 0 and boolean when automatic access
  ;; Bool true will lead to weird access dates (unix time 1) - which would have to be handled later.
  (let [active-modules   #(into #{} (keys (filter-map identity (map-map val-to-bool %))))
        access-date->int (comp str->int #(if (boolean? %) (if % 1 0) %))]
    (mapv (fn [treatment-access]
            (-> treatment-access
                (unserialize-key :module-accesses #(map-map access-date->int %))
                (#(assoc % :modules-active (active-modules (:module-accesses %))))
                (#(assoc % :modules-activation-dates (map-map b-time/from-unix (filter-map (complement zero?) (:module-accesses %)))))
                (#(assoc % :submitted-homeworks (submitted-homeworks %)))
                (dissoc :module-accesses)))
          (db/get-treatment-accesses
            {:user-id user-id}))))


;; ----------------
;;   DB RETRIEVAL
;; ----------------

(defn check-file
  [content]
  (if (bass/uploaded-file (:file-path content))
    content
    (dissoc content :file-path)))

(defn check-content
  [content]
  (if (empty? (:text content))
    (dissoc content :text)
    content))

(defn get-content
  [content-id]
  (-> (db/get-content
        {:content-id content-id}
        #_[:markdown :tabbed :show-example])
      (check-file)
      (check-content)
      (unserialize-key :data-imports)
      ;; Transform true false array for imports into list of imported data
      (#(assoc % :data-imports (keys (filter-map identity (:data-imports %)))))))

(defn get-module-contents*
  [module-ids]
  (->> (db/get-module-contents {:module-ids module-ids})
       (map check-file)
       (filter #(or (:has-text? %) (:file-path %)))))

(defn get-module-contents
  [module-ids]
  (get-module-contents*
    (if (coll? module-ids)
      module-ids
      [module-ids])))

;; --------------------------
;;   CONTENT CATEGORIZATION
;; --------------------------

(defn- categorize-module-contents
  [contents]
  (let [categorized (group-by :type contents)]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     ;; TODO: Handle multiple main texts
     :main-text  (first (get categorized "MainTexts"))}))

(defn get-categorized-module-contents
  [module-id]
  (-> (get-module-contents module-id)
      (categorize-module-contents)))

(defn get-module-contents-with-update-time
  [module-ids treatment-access-id]
  (let [last-updates     (map-map first (group-by :data-name (db/get-content-data-last-save {:data-owner-id treatment-access-id})))
        content-accesses (->> (db/get-content-first-access {:treatment-access-id treatment-access-id :module-ids module-ids})
                              (mapv #(vector (:module-id %) (:content-id %)))
                              (into #{}))
        contents         (->> (get-module-contents module-ids)
                              (mapv #(assoc % :data-updated (get-in last-updates [(:data-name %) :time])))
                              (mapv #(assoc % :accessed? (contains? content-accesses [(:module-id %) (:content-id %)]))))]
    (map-map categorize-module-contents (group-by :module-id contents))))

(defn treatment-map
  [treatment-id]
  (let [info    (-> (db/get-treatment-info
                      {:treatment-id treatment-id})
                    (unserialize-key
                      :modules-automatic-access
                      #(into #{} (keys (filter-map identity (map-map val-to-bool %))))))
        modules (db/get-treatment-modules {:treatment-id treatment-id})]
    (merge info
           {:modules modules})))


#_(defn- convert-dates
    [treatment-access]
    [(tc/from-sql-date (:start-date treatment-access))
     (tc/from-sql-date (:end-date treatment-access))])

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
                 {:active          (active-fn %)
                  :activation-date (activation-date-fn %)
                  :homework-status (homework-status-fn %)})
         (:modules treatment))))

(defn user-components
  [treatment-access treatment]
  {:modules       (modules-component treatment-access treatment)
   :messaging     true
   :send-messages (true? (and (:messages-send-allowed? treatment) (:messages-send-allowed? treatment-access)))})


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
  [treatment-access module]
  (db/retract-homework! {:treatment-access-id (:treatment-access-id treatment-access)
                         :module-id           (:module-id module)}))


(defn register-content-access!
  [content-id module-id treatment-access-id]
  (db/register-content-access!
    {:content-id          content-id
     :module-id           module-id
     :treatment-access-id treatment-access-id}))