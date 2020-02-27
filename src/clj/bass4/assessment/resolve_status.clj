(ns bass4.assessment.resolve-status
  (:require [clj-time.core :as t]))

(defn- get-time-limit
  [{:keys [time-limit is-record? repetition-interval repetition-type]}]
  (when (or (> time-limit 0)
            (and (not is-record?)
                 (= repetition-type "INTERVAL")))
    (apply min (filter (complement zero?) [time-limit repetition-interval]))))

(defn- get-activation-date
  [administration assessment]
  (when-let [activation-date (if (= (:scope assessment) 0)
                               (:participant-activation-date administration)
                               (:group-activation-date administration))]
    (t/plus activation-date (t/hours (:activation-hour assessment)))))

(defn- next-manual-ongoing?
  [{:keys [repetition-type]} next-administration-status]
  (if (nil? next-administration-status)
    false
    (and (= "MANUAL" repetition-type)
         (or (= next-administration-status :assessment-status/waiting)
             (= next-administration-status :assessment-status/ongoing)
             (= next-administration-status :assessment-status/date-passed)
             (= next-administration-status :assessment-status/completed)))))

(defn- get-administration-status
  "Does not know about assessment series"
  [now administration next-administration-status assessment]
  (let [res (merge administration
                   (select-keys assessment [:assessment-id :is-record? :assessment-name :clinician-rated? :scope])
                   {:status (cond
                              (:corrupt? assessment)
                              :assessment-status/corrupt

                              (= (:participant-administration-id administration)
                                 (:group-administration-id administration)
                                 nil)
                              (throw (ex-info "No valid administration" administration))

                              (:deleted? administration)
                              :assessment-status/deleted

                              (and (some? (:date-completed administration))
                                   (> (:date-completed administration) 0))
                              :assessment-status/completed

                              (false? (:participant-administration-active? administration))
                              :assessment-status/user-inactive

                              (false? (:group-administration-active? administration))
                              :assessment-status/group-inactive

                              ;(not (and (if (contains? administration :group-administration-active?)
                              ;            (:group-administration-active? administration)
                              ;            true)
                              ;          (if (contains? administration :participant-administration-active?)
                              ;            (:participant-administration-active? administration)
                              ;            true)))
                              ;:assessment-status/inactive

                              (and (= (:scope assessment) 0)
                                   (nil? (:participant-administration-id administration)))
                              :assessment-status/scoped-missing

                              (and (= (:scope assessment) 1)
                                   (nil? (:group-administration-id administration)))
                              :assessment-status/scoped-missing

                              (> (:assessment-index administration) (:repetitions assessment))
                              :assessment-status/superfluous

                              (next-manual-ongoing? assessment next-administration-status)
                              :assessment-status/date-passed

                              :else
                              (let [activation-date (get-activation-date administration assessment)
                                    time-limit      (get-time-limit assessment)]
                                (cond
                                  ;; REMEMBER:
                                  ;; activation-date is is UTC time of activation,
                                  ;; NOT local time. Thus, it is sufficient to compare
                                  ;; to t/now which returns UTC time
                                  (nil? activation-date)
                                  :assessment-status/no-date

                                  (t/before? now activation-date)
                                  :assessment-status/waiting

                                  (and (some? time-limit) (t/after? now (t/plus activation-date (t/days time-limit))))
                                  :assessment-status/date-passed

                                  :else
                                  :assessment-status/ongoing)))})]
    res))

(defn get-administration-statuses
  "Pure function that takes all of a user's or group's administrations
  for an assessment and returns them with :status key set.
  Does not know about assessment series, meaning that it does not matter if
  the assessment is within or outside the user's or group's assessment series."
  [now administrations assessment]
  (when (seq administrations)
    (let [next-administrations   (get-administration-statuses now (rest administrations) assessment)
          current-administration (first administrations)]
      (when (nil? assessment)
        (throw (Exception. (str "Assessment ID: " (:assessment-id current-administration) " does not exist."))))
      (cons (get-administration-status
              now
              current-administration
              (:status (first next-administrations))
              assessment)
            next-administrations))))