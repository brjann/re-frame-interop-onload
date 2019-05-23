(ns bass4.assessment.administration
  (:require [bass4.assessment.services :as assessments]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.utils :as utils]
            [bass4.assessment.create-missing :as missing]))




;; ------------------------------
;;     COMPLETE ADMINISTRATION
;; ------------------------------

(defn- start-date-representation
  [date-time]
  (tc/to-epoch (bass/local-midnight date-time)))

(defn dependent-assessments!
  "Activates any assessments depending on completed assessments (by administration id)"
  [user-id administration-ids]
  (let [assessments (->> (db/get-dependent-assessments {:administration-ids administration-ids})
                         (map #(assoc % :start-time (-> (t/plus (t/now) (t/days (:offset-days %)))
                                                        (start-date-representation)))))]
    (when (seq assessments)
      (->> (missing/create-missing-administrations! user-id (map #(assoc % :assessment-index 1) assessments))
           (map #(utils/select-values % [:participant-administration-id :start-time]))
           (assoc {} :dates)
           (db/set-administration-dates!)))))

(defn set-administrations-completed!
  [user-id administration-ids]
  (db/set-administration-complete! {:administration-ids administration-ids})
  (db/set-last-assessment! {:administration-id (first administration-ids)})
  (dependent-assessments! user-id administration-ids))

(defn- complete-empty-administrations!
  [user-id assessments]
  (let [empty-administration-ids (map :participant-administration-id (filter #(empty? (:instruments %)) assessments))]
    (when (seq empty-administration-ids)
      (set-administrations-completed! user-id empty-administration-ids))))

(defn instrument-completed!
  [user-id administration-ids instrument-id answers-map]
  (db/set-instrument-completed! {:user-id user-id :instrument-id instrument-id})
  (instrument-answers/save-administrations-answers! administration-ids instrument-id answers-map))

(defn check-completed-administrations!
  [user-id round completed-instrument-id]
  (let [non-empty                (->> round
                                      (remove #(= completed-instrument-id (:instrument-id %)))
                                      (map :administration-id)
                                      (distinct))
        empty-administration-ids (utils/diff (map :administration-id round) non-empty)]
    (when (seq empty-administration-ids)
      (set-administrations-completed! user-id empty-administration-ids))))

;; ------------------------
;;     ROUNDS CREATION
;; ------------------------

(defn- merge-batches
  [coll val]
  (if (and (seq coll) (:allow-swallow? val))
    (concat (butlast coll) (list (concat (last coll) (list val))))
    (concat coll (list (list val)))))

(defn- batch-texts
  [text-name]
  (fn
    [batch]
    (let [texts (remove
                  #(some (partial = %) [nil ""])
                  (map-indexed
                    (fn [idx assessment] (when (or (zero? idx) (:show-texts-if-swallowed? assessment)) (get assessment text-name)))
                    batch))]
      (when (seq texts)
        ;; Since these are saved to a table, they are converted to text representation
        {:texts (pr-str texts)}))))

(defn- assessment-instruments
  [assessment]
  (if (= (:shuffle-instruments assessment) 1) (shuffle (:instruments assessment)) (:instruments assessment)))

(defn- batch-instruments
  [batch]
  (flatten
    (map (fn [assessment]
           (map #(do {:administration-id (:participant-administration-id assessment) :instrument-id %})
                (assessment-instruments assessment)))
         batch)))

(defn- batch-steps
  [idx batch]
  (let [welcome     ((batch-texts :welcome-text) batch)
        thank-you   ((batch-texts :thank-you-text) batch)
        instruments (batch-instruments batch)]
    ;; Does not handle empty stuff? Use concat instead of list
    (map #(merge {:batch-id idx} %) (flatten (remove empty? (list welcome instruments thank-you))))))

(defn- step-row
  [user-id]
  (fn [idx step]
    (merge
      {:time              (t/now) #_(tc/to-sql-date (t/now))
       :user-id           user-id
       :batch-id          nil
       :step              idx
       :texts             nil
       :instrument-id     nil
       :administration-id nil}
      step)))

(defn- save-round!
  [round]
  (let [{:keys [round-id]} (db/get-new-round-id!)]
    (db/insert-assessment-round! {:rows (map #(cons round-id %) (map vals round))})))

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
  (let [pending-assessments (assessments/ongoing-assessments user-id)
        round-count         (when (seq pending-assessments)
                              (complete-empty-administrations! user-id pending-assessments)
                              (let [round (generate-assessment-round user-id pending-assessments)]
                                (when (seq round)
                                  (save-round! round))))]
    (or round-count 0)))

;; ------------------------
;;  ROUNDS ADMINISTRATION
;; ------------------------

;; 1. When user logs in - create round entries
;; 2. If created round entries > 0, set :assessments-pending? in session to true
;; 3. Whenever trying to access /user, check if :assessments-pending? is true
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

;;
;; ASSESSMENT START TIME
;;
;; BASS saves assessment start dates in UTC time,
;; i.e., an assessment that starts in a Swedish database on May 24
;; is recorded as starting on May 23 22:00 GMT.
;;
;; Which means that
;; - the start time should be created as UTC corresponding to midnight in selected timezone
;; - the start time should be compared to (t/now)
;;

(defn get-assessment-round [user-id]
  (let [order-count (fn [x]
                      (let [instruments (remove nil? (distinct (map :instrument-id x)))]
                        (map #(merge % {:instrument-count (count instruments)
                                        :instrument-order (->> instruments
                                                               (utils/indices (partial = (:instrument-id %)))
                                                               (first)
                                                               (utils/fnil+ inc))})
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