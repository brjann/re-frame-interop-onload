(ns bass4.assessment.resolve-status
  (:require [clj-time.core :as t]))

(defn- get-time-limit
  [{:keys [time-limit is-record repetition-interval repetition-type]}]
  (when
    (or (> time-limit 0) (and (not is-record) (= repetition-type "INTERVAL")))
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
         (or (= next-administration-status ::as-waiting)
             (= next-administration-status ::as-ongoing)
             (= next-administration-status ::as-date-passed)
             (= next-administration-status ::as-completed)))))

(defn- get-administration-status
  "Does not know about assessment series"
  [now administration next-administration-status assessment]
  (merge administration
         (select-keys assessment [:assessment-id :is-record? :assessment-name :clinician-rated? :scope])
         {:status (cond
                    (= (:participant-administration-id administration)
                       (:group-administration-id administration)
                       nil)
                    (throw (ex-info "No valid administration" administration))

                    (:deleted? administration)
                    ::as-deleted

                    (and (some? (:date-completed administration))
                         (> (:date-completed administration) 0))
                    ::as-completed

                    (false? (:participant-administration-active? administration))
                    ::as-user-inactive

                    (false? (:group-administration-active? administration))
                    ::as-group-inactive

                    ;(not (and (if (contains? administration :group-administration-active?)
                    ;            (:group-administration-active? administration)
                    ;            true)
                    ;          (if (contains? administration :participant-administration-active?)
                    ;            (:participant-administration-active? administration)
                    ;            true)))
                    ;::as-inactive

                    (and (= (:scope assessment) 0)
                         (nil? (:participant-administration-id administration)))
                    ::as-scoped-missing

                    (and (= (:scope assessment) 1)
                         (nil? (:group-administration-id administration)))
                    ::as-scoped-missing

                    (> (:assessment-index administration) (:repetitions assessment))
                    ::as-superfluous

                    (next-manual-ongoing? assessment next-administration-status)
                    ::as-date-passed

                    :else
                    (let [activation-date (get-activation-date administration assessment)
                          time-limit      (get-time-limit assessment)]
                      (cond
                        ;; REMEMBER:
                        ;; activation-date is is UTC time of activation,
                        ;; NOT local time. Thus, it is sufficient to compare
                        ;; to t/now which returns UTC time
                        (nil? activation-date)
                        ::as-no-date

                        (t/before? now activation-date)
                        ::as-waiting

                        (and (some? time-limit) (t/after? now (t/plus activation-date (t/days time-limit))))
                        ::as-date-passed

                        :else
                        ::as-ongoing)))}))

(defn get-administration-statuses
  "Does not know about assessment series"
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