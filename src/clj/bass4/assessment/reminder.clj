(ns bass4.assessment.reminder
  (:require [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.create-missing :as missing]
            [clojure.string :as str]))

(defn- db-activated-participant-administrations
  [db date-min date-max hour]
  (db/get-activated-participant-administrations db {:date-min date-min
                                                    :date-max date-max
                                                    :hour     hour}))

(defn- db-activated-group-administrations
  [db date-min date-max hour]
  (db/get-activated-group-administrations db {:date-min date-min
                                              :date-max date-max
                                              :hour     hour}))

(defn- db-late-participant-administrations
  [db date]
  (db/get-late-participant-administrations db {:date date}))

(defn- db-late-group-administrations
  [db date]
  (db/get-late-group-administrations db {:date date}))

(defn- db-users-assessment-series-id
  [db user-ids]
  (when (seq user-ids)
    (let [res (db/get-user-assessment-series db {:user-ids user-ids})]
      (into {} (map #(vector (:user-id %) (:assessment-series-id %))) res))))

(defn- db-groups-assessment-series-id
  [db group-ids]
  (when (seq group-ids)
    (let [res (db/get-group-assessment-series db {:group-ids group-ids})]
      (into {} (map #(vector (:group-id %) (:assessment-series-id %))) res))))

(defn- db-participant-administrations-by-user+assessment+series
  [db user+assessments+series]
  (db/get-participant-administrations-by-user+assessment
    db
    {:user-ids+assessment-ids user+assessments+series}))

(defn db-group-administrations-by-group+assessment+series
  [db groups+assessments+series]
  (db/get-group-administrations-by-group+assessment db {:group-ids+assessment-ids groups+assessments+series}))

(defn db-assessments
  [db assessment-ids]
  (when assessment-ids
    (->> (db/get-remind-assessments db {:assessment-ids assessment-ids})
         (map #(vector (:assessment-id %) %))
         (into {}))))

(defn db-activation-reminders-sent!
  [db administrations]
  (when (seq administrations)
    (db/activation-reminders-sent! db {:administration-ids (map :participant-administration-id administrations)})))

(defn db-late-reminders-sent!
  [db administrations]
  (when (seq administrations)
    (db/late-reminders-sent! db {:remind-numbers (map #(vector (:participant-administration-id %)
                                                               (::remind-number %))
                                                      administrations)})))

(defn today-midnight
  [now tz]
  (-> (t/to-time-zone now tz)
      (t/with-time-at-start-of-day)
      (utils/to-unix)))

(defn today-last-second
  [now tz]
  (-> (t/to-time-zone now tz)
      (t/with-time-at-start-of-day)
      (t/plus (t/days 1))
      (utils/to-unix)
      (- 1)))

(defn potential-activation-reminders
  "Returns list of potentially activated assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,
   ::remind-type :activation}"
  [db now tz]
  (let [hour                        (t/hour (t/to-time-zone now tz))
        date-min                    (today-midnight now tz)
        date-max                    (today-last-second now tz)
        participant-administrations (db-activated-participant-administrations db date-min date-max hour)
        group-administrations       (db-activated-group-administrations db date-min date-max hour)]
    (->> (concat participant-administrations
                 group-administrations)
         (map #(assoc % ::remind-type ::activation)))))

(defn- potential-late-reminders
  "Returns list of potentially late assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,
   ::remind-type :late}"
  [db now]
  (let [participant-administrations (db-late-participant-administrations db now)
        group-administration        (db-late-group-administrations db now)]
    (->> (concat participant-administrations
                 group-administration)
         (map #(assoc % ::remind-type ::late)))))

(defn- participant-administrations-from-potential-assessments
  "Returns all administrations for user-assessment combo,
  grouped by [user-id assessment-id"
  [db potential-assessments]
  (let [assessment-series       (->> potential-assessments
                                     (map :user-id)
                                     (db-users-assessment-series-id db))
        user+assessments+series (->> potential-assessments
                                     (map #(vector (:user-id %)
                                                   (:assessment-id %)
                                                   (get assessment-series (:user-id %))))
                                     (into #{}))
        administrations         (db-participant-administrations-by-user+assessment+series
                                  db user+assessments+series)]
    (group-by #(vector (:user-id %) (:assessment-id %)) administrations)))

(defn- group-administrations-from-potential-assessments
  "Returns all administrations for group-assessment combo,
  grouped by [group-id assessment-id"
  [db potential-assessments]
  (let [potential-assessments     (filter :group-id potential-assessments)
        assessment-series         (->> potential-assessments
                                       (map :group-id)
                                       (db-groups-assessment-series-id db))
        groups+assessments+series (->> potential-assessments
                                       (map #(vector (:group-id %)
                                                     (:assessment-id %)
                                                     (get assessment-series (:group-id %))))
                                       (into #{}))
        administrations           (db-group-administrations-by-group+assessment+series
                                    db groups+assessments+series)]
    (group-by #(vector (:group-id %) (:assessment-id %)) administrations)))

;; --------------
;; HOW THIS WORKS
;; --------------
;; Premise:
;;   - I can't manage to figure out if it is possible to execute a single SQL statement
;;     to get all active group and participant administrations.
;;   - All administrations belonging to an assessment are needed to determine if one
;;     administration is ongoing (because of manual repetition - but interval repetition
;;     should also be modified to only allow one administrations to be active
;;     (and manual should too!)
;;   - Ongoing assessment may be in wrong assessment series and therefore need to be
;;     qualified.
;;
;; Steps:
;;   1. Get potentially active participant and group administrations.
;;   2. Get ALL p and g administrations of the assessments of these p and g administrations
;;   3. Combine these administrations into a format acceptable to the assessment-ongoing functions
;;   4. Check if administrations are ongoing
;;

;; TESTS THAT NEED TO BE WRITTEN
;;   - Assessment from other project

(defn- merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by :assessment-index)
       (partition-by :assessment-index)
       (map (partial apply merge))
       (map #(assoc % :active? (and (or (:group-administration-active? %) true)
                                    (or (:participant-administration-active? %) true))
                      :user-id user-id))))

(defn- ongoing-from-potentials
  "Returns list of ALL ongoing assessments based on list of potentials.
  Note, ALL means that ongoing assessment that are not part of potentials may be returned"
  [db now potentials]
  (let [user-groups                         (->> potentials
                                                 (map #(vector (:user-id %) (:group-id %)))
                                                 (filter #(second %))
                                                 (into {}))
        participant-administrations-grouped (participant-administrations-from-potential-assessments db potentials)
        group-administrations-grouped       (when-not (empty? user-groups)
                                              (group-administrations-from-potential-assessments db potentials))
        assessments'                        (db-assessments db (->> potentials
                                                                    (map :assessment-id)
                                                                    (into #{})))
        merged-by-user+assessment           (->> potentials
                                                 (map #(vector (:user-id %) (:assessment-id %)))
                                                 (map (fn [[user-id assessment-id]]
                                                        (let [group-id                    (get user-groups user-id)
                                                              participant-administrations (get participant-administrations-grouped [user-id assessment-id])
                                                              group-administrations       (when group-id
                                                                                            (get group-administrations-grouped [group-id assessment-id]))
                                                              merged-administrations      (merge-participant-group-administrations
                                                                                            user-id
                                                                                            participant-administrations
                                                                                            group-administrations)]
                                                          [[user-id assessment-id] merged-administrations])))
                                                 (into {}))
        ongoing-assessments                 (->> merged-by-user+assessment
                                                 (mapv (fn [[[_ assessment-id] administrations]]
                                                         (assessment-ongoing/ongoing-administrations
                                                           now administrations (get assessments' assessment-id))))
                                                 (flatten)
                                                 (map #(merge % (get assessments' (:assessment-id %)))))]
    ongoing-assessments))

(defn ongoing-reminder-assessments
  [db now potentials]
  (when (seq potentials)
    (let [ongoing-assessments                (ongoing-from-potentials db now potentials)
          potential-by+user+assessment+index (->> potentials
                                                  (map #(vector [(:user-id %) (:assessment-id %) (:assessment-index %)] %))
                                                  (into {}))
          filtered-ongoing-potentials        (->> ongoing-assessments
                                                  (map (fn [ongoing]
                                                         (let [potential (get potential-by+user+assessment+index
                                                                              [(:user-id ongoing)
                                                                               (:assessment-id ongoing)
                                                                               (:assessment-index ongoing)])]
                                                           (assoc ongoing ::remind-type (::remind-type potential)))))
                                                  (filter ::remind-type))]
      filtered-ongoing-potentials)))

(defn- add-late-remind-number-fn
  [now]
  (fn [remind-assessment]
    (let [activation-date       (if (= 0 (:scope remind-assessment))
                                  (:participant-activation-date remind-assessment)
                                  (:group-activation-date remind-assessment))
          days-since-activation (t/in-days (t/interval activation-date now))
          remind-interval       (:late-remind-interval remind-assessment)
          remind-count          (:late-remind-count remind-assessment)
          max-days              (* remind-interval remind-count)
          remind-number         (int (/ days-since-activation remind-interval))
          reminders-sent        (or (:late-reminders-sent remind-assessment) 0)]
      (assoc remind-assessment ::remind-number (cond
                                                 (< max-days days-since-activation)
                                                 nil

                                                 (= 0 remind-number)
                                                 nil

                                                 (> remind-number remind-count)
                                                 nil

                                                 (>= reminders-sent remind-number)
                                                 nil

                                                 :else
                                                 remind-number)))))

(defn reminders
  [db now tz]
  (let [potential-activations (potential-activation-reminders db now tz)
        potential-late        (potential-late-reminders db now)
        reminders-by-type     (->> (concat potential-activations
                                           potential-late)
                                   (ongoing-reminder-assessments db now)
                                   (group-by ::remind-type))
        remind-activation     (::activation reminders-by-type)
        remind-late           (->> (::late reminders-by-type)
                                   (map (add-late-remind-number-fn now))
                                   (filter ::remind-number))]
    (concat remind-activation remind-late)))


;; https://clojure.org/guides/comparators
;; A 3-way comparator takes 2 values, x and y, and returns
;; -1 if x comes before y,
;; 1 if x comes after y, or
;; 0 if they are equal

(defn quick-url?
  [assessment]
  (when (:remind-message assessment)
    (str/includes? (:remind-message assessment) "{QUICKURL}")))

(defn- assessment-comparator
  [x y]
  (let [quick-url-x? (quick-url? (:remind-message x))
        quick-url-y? (quick-url? (:remind-message y))]
    (cond
      ;; {QUICKURL} wins
      (and quick-url-x?
           (not quick-url-y?))
      -1

      (and quick-url-y?
           (not quick-url-x?))
      1

      ;; Custom message wins
      (and (not (str/blank? (:remind-message x)))
           (str/blank? (:remind-message y)))
      -1

      (and (not (str/blank? (:remind-message y)))
           (str/blank? (:remind-message x)))
      1

      ;; Priority wins
      (not= (:priority x) (:priority y))
      (compare (:priority x) (:priority y))

      ;; Lastly by sort order
      :else
      (compare (:sort-order x) (:sort-order y)))))

(defn- user-messages
  [assessments]
  (let [email (->> assessments
                   (filter :activation-email?)
                   (sort assessment-comparator)
                   (first))
        sms   (->> assessments
                   (filter :activation-sms?)
                   (sort assessment-comparator)
                   (first))]
    (filter identity [(when email (assoc email ::message-type :email))
                      (when sms (assoc sms ::message-type :sms))])))

(defn remind-messages
  [remind-assessments]
  (->> remind-assessments
       (group-by :user-id)
       (vals)
       (mapcat user-messages)))

(defn remind!
  [db now tz]
  (let [remind-assessments  (-> (reminders db now tz)
                                (missing/add-missing-administrations!))
        message-assessments (remind-messages remind-assessments)
        reminders-by-type   (group-by ::remind-type remind-assessments)]
    (db-activation-reminders-sent! db (::activation reminders-by-type))
    (db-late-reminders-sent! db (::late reminders-by-type))))

(def tz (t/time-zone-for-id "Asia/Tokyo"))
