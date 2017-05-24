(ns bass4.services.assessments
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+ diff in?]]
            [clj-time.coerce]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [bass4.services.instrument-answers :as instrument-answers]))


;; ------------------------------
;; CREATE MISSING ADMINISTRATIONS
;; ------------------------------

(defn- create-administrations-objects!
  [user-id missing-administrations]
  (create-bass-objects-without-parent!
    "cParticipantAdministration"
    "Administrations"
    (count missing-administrations)))

(defn- delete-administration!
  [administration-id]
  (log/info "Deleting surplus administrations " administration-id)
  (db/delete-object-from-objectlist! {:object-id administration-id})
  (db/delete-participant-administration! {:administration-id administration-id}))

(defn- update-created-administrations!
  [user-id new-object-ids missing-administrations]
  ;; mapv because not lazy
  (mapv (fn [administration-id {:keys [assessment-index assessment-id]}]
          (try
            ;; Try to update the placeholder with the assessment and index
            (db/update-new-participant-administration!
              {:administration-id administration-id :user-id user-id :assessment-id assessment-id :assessment-index assessment-index})
            (db/update-objectlist-parent! {:object-id administration-id :parent-id user-id})
            (db/link-property-reverse! {:linkee-id administration-id :property-name "Assessment" :linker-class "cParticipantAdministration"})
            administration-id
            ;; If that fails, then delete the placeholder and return instead the
            ;; duplicate administration's id.
            (catch Exception e
              (delete-administration! administration-id)
              (:administration-id (db/get-administration-by-assessment-and-index {:user-id user-id :assessment-id assessment-id :assessment-index assessment-index})))))
        new-object-ids
        missing-administrations))

(defn- create-missing-administrations!
  [user-id missing-administrations]
  (let [new-object-ids
        (update-created-administrations!
          user-id
          (create-administrations-objects! user-id missing-administrations)
          missing-administrations)]
    (map #(assoc %1 :participant-administration-id %2) missing-administrations new-object-ids)))


(defn- insert-new-into-old
  [new-administrations old-administrations]
  (map
    (fn [old]
      (if (nil? (:participant-administration-id old))
        (conj old (first (filter
                           #(and
                              (= (:assessment-id old) (:assessment-id %1))
                              (= (:assessment-index old) (:assessment-index %1)))
                           new-administrations)))
        old)
      )
    old-administrations))

(defn- get-missing-administrations
  [matching-administrations]
  (map
    #(select-keys % [:assessment-id :assessment-index])
    (filter #(nil? (:participant-administration-id %)) matching-administrations)))

(defn- add-missing-administrations
  [user-id matching-administrations]
  (let [missing-administrations (get-missing-administrations matching-administrations)]
    (if (> (count missing-administrations) 0)
      (insert-new-into-old (create-missing-administrations! user-id missing-administrations) matching-administrations)
      matching-administrations)))


;; ------------------------
;; GET PENDING ASSESSMENTS
;; ------------------------

(defn- get-user-administrations
  [user-id]
  (let [group-id (:group-id (db/get-user-group {:user-id user-id}))
        assessment-series-id (:assessment-series-id (db/get-user-assessment-series {:user-id user-id}))
        assessments (db/get-assessment-series-assessments {:assessment-series-id assessment-series-id})
        administrations (db/get-user-administrations {:user-id user-id :group-id group-id :assessment-series-id assessment-series-id})]
    {:administrations (group-by #(:assessment-id %) administrations) :assessments (key-map-list assessments :assessment-id)}))


(defn- get-time-limit [{:keys [time-limit is-record repetition-interval repetition-type]}]
  (when
    (or (> time-limit 0) (and (zero? is-record) (= repetition-type "INTERVAL")))
    (apply min (filter (complement zero?) [time-limit repetition-interval]))))

(defn- get-activation-date [administration assessment]
  (when-let [activation-date (if (= (:scope assessment) 0)
                               (:participant-activation-date administration)
                               (:group-activation-date administration))]
    (t/plus (clj-time.coerce/from-sql-date
              activation-date) (t/hours (:activation-hour assessment)))))

(defn- check-next-status [{:keys [repetition-type]} next-administration-status]
  (if (nil? next-administration-status)
    false
    (and (= repetition-type "MANUAL")
         (and (not= next-administration-status "AS_NO_DATE") (not= next-administration-status "AS_INACTIVE")))))


(defn- get-administration-status [administration next-administration-status assessment]
  {:assessment-id    (:assessment-id assessment)
   :assessment-index (:assessment-index administration)
   :is-record        (:is-record assessment)
   :assessment-name  (:assessment-name assessment)
   :clinician-rated  (:clinician-rated assessment)
   :status           (cond
                       (= (:participant-administration-id administration) (:group-administration-id administration) nil) "AS_ALL_MISSING"
                       (and (= (:scope assessment) 0) (nil? (:participant-administration-id administration))) "AS_OWN_MISSING"
                       (and (= (:scope assessment) 1) (nil? (:group-administration-id administration))) "AS_GROUP_MISSING"
                       (> (:date-completed administration) 0) "AS_COMPLETED"
                       (> (:assessment-index administration) (:repetitions assessment)) "AS_SUPERFLUOUS"
                       (zero? (:active administration)) "AS_INACTIVE"
                       (check-next-status assessment next-administration-status) "AS_DATE_PASSED"
                       :else (let [activation-date (get-activation-date administration assessment)
                                   time-limit (get-time-limit assessment)]
                               (cond
                                 (nil? activation-date) "AS_NO_DATE"
                                 (t/before? (t/now) activation-date) "AS_WAITING"
                                 (and (some? time-limit) (t/after? (t/now) (t/plus activation-date (t/days time-limit)))) "AS_DATE_PASSED"
                                 :else "AS_PENDING")))})

(defn- get-assessment-statuses [administrations assessments]
  (when (seq administrations)
    (let [next-administrations (get-assessment-statuses (rest administrations) assessments)
          current-administration (first administrations)
          current-assessment (get assessments (:assessment-id current-administration))]
      (cons (get-administration-status current-administration (last (first next-administrations)) current-assessment) next-administrations))))


(defn- filter-pending-assessments [assessment-statuses]
  (filter #(and
             (= (:status %) "AS_PENDING")
             (zero? (:is-record %))
             (zero? (:clinician-rated %)))
          assessment-statuses))

(defn matching-administration
  [assessment-index administrations]
  (filter #(= (:assessment-index %) assessment-index) administrations))


(defn- collect-assessment-administrations
  [administrations pending-assessments]

  ;; This function assumes that there is a row for all missing administrations
  ;; with a nil-value for participant-administration-id. I can't imagine that
  ;; there could be an empty row.
  ;; If such a case could exist, the map would have to be enclosed in a map
  ;; that adds the missing assessment-id and assessment-index to the result.
  ;; Remove flatten
  ;;
  ;; (map
  ;;  #(merge (first %1) %2)
  ;;  X
  ;;  (select-keys pending-assessments [:assessment-id :assessment-index]))

  (flatten
    (map
      (fn [{:keys [assessment-id assessment-index]}]
        (matching-administration
          assessment-index
          (get administrations assessment-id)))
      pending-assessments)))

(defn- add-instruments [assessments]
  (let [administration-ids (map :participant-administration-id assessments)
        assessment-instruments (->> {:assessment-ids (map :assessment-id assessments)}
                                    (db/get-assessments-instruments)
                                    (group-by :assessment-id)
                                    (map-map #(map :instrument-id %)))
        completed-instruments (->> {:administration-ids administration-ids}
                                   (db/get-administration-completed-instruments)
                                   (group-by :administration-id)
                                   (map-map #(map :instrument-id %)))
        additional-instruments (->> {:administration-ids administration-ids}
                                    (db/get-administration-additional-instruments)
                                    (group-by :administration-id)
                                    (map-map #(map :instrument-id %)))]
    (map #(assoc % :instruments (diff
                                  (concat
                                    (get assessment-instruments (:assessment-id %))
                                    (get additional-instruments (:participant-administration-id %)))
                                  (get completed-instruments (:participant-administration-id %))))
         assessments)))

(defn- complete-empty-administrations!
  [assessments]
  (let [empty-administration-ids (map :participant-administration-id (filter #(empty? (:instruments %)) assessments))]
    (db/set-administration-complete! {:administration-ids empty-administration-ids})))


(defn get-pending-assessments [user-id]
  (let
    ;; NOTE that administrations is a map of lists
    ;; administrations within one assessment battery
    ;;
    ;; Amazingly enough, this all works even with no pending administrations
    [{:keys [administrations assessments]} (get-user-administrations user-id)]
    (->> (vals administrations)
         ;; Sort administrations by their assessment-index
         (map #(sort-by :assessment-index %))
         ;; Return assessment (!) statuses
         (map #(get-assessment-statuses % assessments))
         ;; Remove lists within list
         (flatten)
         ;; Keep the assessments that are AS_PENDING
         (filter-pending-assessments)
         ;; Find corresponding administrations
         (collect-assessment-administrations administrations)
         ;; Add any missing administrations
         (add-missing-administrations user-id)
         ;; Merge assessment and administration info into one map
         (map #(merge % (get assessments (:assessment-id %))))
         (add-instruments))))


;; ------------------------
;;     ROUNDS CREATION
;; ------------------------

(defn merge-batches
  [coll val]
  (if (and (seq coll) (= (:allow-swallow val) 1))
    (concat (butlast coll) (list (concat (last coll) (list val))))
    (concat coll (list (list val)))))

(defn batch-texts
  [text-name]
  (fn
    [batch]
    (pr-str (remove
              #(some (partial = %) [nil ""])
              (map-indexed
                (fn [idx assessment] (when (or (= idx 0) (= (:show-texts-if-swallowed assessment))) (get assessment text-name)))
                batch)))))

(defn assessment-instruments
  [assessment]
  (if (= (:shuffle-instruments assessment) 1) (shuffle (:instruments assessment)) (:instruments assessment)))

(defn batch-instruments
  [batch]
  (flatten
    (map (fn [assessment]
           (map #(do {:administration-id (:participant-administration-id assessment) :instrument-id %})
                (assessment-instruments assessment)))
         batch)))

(defn batch-steps
  [idx batch]
  (let [welcome {:texts ((batch-texts :welcome-text) batch)}
        thank-you {:texts ((batch-texts :thank-you-text) batch)}
        instruments (batch-instruments batch)]
    ;; Does not handle empty stuff? Use concat instead of list
    (map #(merge {:batch-id idx} %) (flatten (list welcome instruments thank-you)))))

(defn step-row
  [user-id]
  (fn [idx step]
    (merge
      ;; TODO: What is the timezone of this? UTC?
      {:time              (clj-time.coerce/to-sql-date (t/now))
       :user-id           user-id
       :batch-id          nil
       :step              idx
       :texts             nil
       :instrument-id     nil
       :administration-id nil}
      step)))

(defn save-round!
  [round]
  (try
    ;; Lock table to make sure that round-id is unique
    (db/lock-assessment-rounds-table!)
    (let [round-id (or (:round-id (db/get-new-round-id)) 0)]
      (db/insert-assessment-round! {:rows (map #(cons round-id %) (map vals round))}))
    (finally
      (db/unlock-tables!))))


(defn generate-assessment-round
  [user-id pending-assessments]
  (->> pending-assessments
       ;; Sort assessments by priority
       (sort-by :priority)
       ;; Create rounds of merged assessments, according to "allow-swallow" setting
       (reduce merge-batches ())
       ;; Create round steps
       (map-indexed batch-steps)
       ;; Remove lists within batches
       (flatten)
       ;; Create step db rows
       (map-indexed (step-row user-id))))

(defn create-assessment-round-entries!
  "Returns number of round entries created"
  [user-id]
  (let [pending-assessments (get-pending-assessments user-id)]
    (if (seq pending-assessments)
      (do
        (complete-empty-administrations! pending-assessments)
        (save-round! (generate-assessment-round user-id pending-assessments)))
      0)))


;; ------------------------
;;  ROUNDS ADMINISTRATION
;; ------------------------

;; 1. When user logs in - create round entries
;; 2. If created round entries > 0, set :assessments-pending in session to true
;; 3. Whenever trying to access /user, check if :assessments-pending is true
;; 4. If so, check if there are assessment round entries in table
;;    no -> change session and redirect
;; 5. If yes, hand over control to assessment shower
;;
;; Concurrency scenarios
;; 1. Repeated/parallel submission of data. Will overwrite old answers in the interval
;; between the retrieval of rounds and set round instruments completed
;; Repeated submission is moderately likely in the case of DB/connection slowness.
;; Parallel login is very unlikely.
;; Impact limited - repeated submissions are likely of same answers.
;;
;; 2. Parallel login. If login occurs before answers have been saved, then
;; a new round will be generated where the instruments are not considered completed.
;; Very unlikely.
;; Impact moderate - user has to answer instruments again
;;

(defn get-assessment-round [user-id]
  (let [order-count (fn [x]
                      (let [instruments (remove nil? (distinct (map :instrument-id x)))]
                        (map #(merge % {:instrument-count (count instruments)
                                        :instrument-order (->> instruments
                                                               (indices (partial = (:instrument-id %)))
                                                               (first)
                                                               (fnil+ inc))})
                             x)))]
    (->> (db/get-current-assessment-round {:user-id user-id})
         ;; Add instrument order and total number of instruments to each step
         (order-count)
         ;; Remove already completed instruments
         (filter (comp not :completed))
         ;; The following removes empty batches (only texts, no instruments).
         ;; Keep texts that should be shown.
         (group-by :batch-id)
         (filter #(seq (filter (some-fn :instrument-id :must-show-texts) (val %1))))
         (vals)
         (flatten))))

(defn batch-must-show-texts!
  [step]
  (db/set-batch-must-show-texts! {:round-id (:round-id step) :batch-id (:batch-id step)}))

(defn step-completed!
  [step]
  (db/set-step-completed! {:id (:id step)}))

(defn instrument-completed!
  [user-id administration-ids instrument-id answers-map]
  (db/set-instrument-completed! {:user-id user-id :instrument-id instrument-id})
  (instrument-answers/save-administrations-answers! administration-ids instrument-id answers-map))

(defn administrations-completed!
  [round completed-instrument-id]
  (let [non-empty (->> round
                       (remove #(= completed-instrument-id (:instrument-id %)))
                       (map :administration-id)
                       (distinct))
        empty-administration-ids (diff (map :administration-id round) non-empty)]
    (when (seq empty-administration-ids)
      (db/set-administration-complete! {:administration-ids empty-administration-ids}))))
