(ns bass4.test.assessment-utils
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [chan alts!! timeout put! <! go-loop]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as tc]
            [bass4.test.core :refer :all]
            [bass4.utils :as utils]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.create-missing :as missing]
            [bass4.routes.quick-login :as quick-login]
            [bass4.services.bass :as bass]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.statuses :as assessment-statuses]
            [clojure.tools.logging :as log]
            [bass4.assessment.flagger :as assessment-flagger]))

(use-fixtures
  :once
  test-fixtures)

(def project-ass1-id 653627)
(def project-ass1-pcollection-id 653628)
(def project-ass2-id 658098)
(def project-ass2-pcollection-id 658099)

(def ass-G-s-2-3-p0 653630)
(def ass-G-week-e+s-3-4-p10 653631)
(def ass-I-s-0-p100-message 653632)
(def ass-I-week-noremind 653633)
(def ass-I-manual-s-5-10-q 653634)
(def ass-I-clinician 654215)
(def ass-I-hour8-2-20 654411)

(def p2-ass-I1 658101)
(def p2-ass-I2 658106)
(def p2-ass-G 658102)

(def ass-flag-assessment-series 653629)

(def assessment-ids [ass-G-s-2-3-p0
                     ass-G-week-e+s-3-4-p10
                     ass-I-s-0-p100-message
                     ass-I-week-noremind
                     ass-I-manual-s-5-10-q
                     ass-I-clinician
                     ass-I-hour8-2-20])

(defn create-group!
  []
  (:objectid (db/create-bass-object! {:class-name    "cGroup"
                                      :parent-id     project-ass1-id
                                      :property-name "Groups"})))

(defn create-assessment!
  [properties]
  (let [assessment-id (:objectid (db/create-bass-object! {:class-name    "cAssessment"
                                                          :parent-id     ass-flag-assessment-series
                                                          :property-name "Assessments"}))]
    (bass/update-object-properties! "c_assessment"
                                    assessment-id
                                    (merge {"Name"                                     (str "Assessment " assessment-id)
                                            "ShuffleInstruments"                       0
                                            "Scope"                                    0
                                            "Type"                                     ""
                                            "RepetitionType"                           0
                                            "Repetitions"                              0
                                            "TimeLimit"                                0
                                            "SendSMSWhenActivated"                     0
                                            "SendEmailWhenActivated"                   0
                                            "RemindParticipantsWhenLate"               0
                                            "RemindInterval"                           1
                                            "MaxRemindCount"                           0
                                            "CustomReminderMessage"                    ""
                                            "ActivatedEmailSubject"                    "Information"
                                            "ReminderEmailSubject"                     "Reminder"
                                            "UseStandardMessage"                       1
                                            "FlagParticipantWhenActivated"             0
                                            "FlagParticipantWhenLate"                  0
                                            "DayCountUntilLate"                        0
                                            "CustomRepetitionInterval"                 0
                                            "IsRecord"                                 0
                                            "CompetingAssessmentsPriority"             10
                                            "CompetingAssessmentsAllowSwallow"         1
                                            "CompetingAssessmentsShowTextsIfSwallowed" 0
                                            "CreateNewQuickLoginOnActivation"          0
                                            "ClinicianAssessment"                      0
                                            "ActivationHour"                           0
                                            "Deleted"                                  0}
                                           properties))
    assessment-id))

(defn additional-instruments!
  [administration-id instruments-ids]
  (doseq [instrument-id instruments-ids]
    (db/create-bass-link! {:linker-id     administration-id
                           :linkee-id     instrument-id
                           :link-property "AdditionalInstruments"
                           :linker-class  "cParticipantAdministration"
                           :linkee-class  "cInstrument"})))

(defn create-group-administration!
  [group-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cGroupAdministration"
                                                              :parent-id     group-id
                                                              :property-name "Administrations"}))]
    (when properties
      (bass/update-object-properties! "c_groupadministration"
                                      administration-id
                                      (merge {"assessment"      assessment-id
                                              "assessmentindex" assessment-index
                                              "active"          1}
                                             properties)))
    administration-id))

(defn create-participant-administration!
  [user-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cParticipantAdministration"
                                                              :parent-id     user-id
                                                              :property-name "Administrations"}))]
    (bass/update-object-properties! "c_participantadministration"
                                    administration-id
                                    (merge {"assessment"      assessment-id
                                            "assessmentindex" assessment-index
                                            "active"          1
                                            "deleted"         0}
                                           properties))
    administration-id))

(defn create-custom-assessment*!
  [user-id]
  (let [assessment-id (:objectid (db/create-bass-object! {:class-name    "cAssessment"
                                                          :parent-id     user-id
                                                          :property-name "AdHocAssessments"}))]
    (bass/update-object-properties! "c_assessment"
                                    assessment-id
                                    {"scope"                            0
                                     "repetitions"                      1
                                     "repetitiontype"                   0
                                     "type"                             ""
                                     "customlabel"                      "ADHOC"
                                     "activationhour"                   0
                                     "timelimit"                        0
                                     "customrepetitioninterval"         0
                                     "isrecord"                         0
                                     "competingassessmentspriority"     0
                                     "competingassessmentsallowswallow" 0
                                     "clinicianassessment"              0})
    assessment-id))

(defn create-custom-assessment!
  [user-id instrument-ids date]
  (let [assessment-id     (create-custom-assessment*! user-id)
        administration-id (create-participant-administration! user-id assessment-id 1 {:date date})]
    (additional-instruments! administration-id instrument-ids)))

#_(defn clear-administrations!
    []
    (let [qmarks (apply str (interpose \, (repeat (count assessment-ids) \?)))]
      (jdbc/with-db-transaction [db db/*db*])
      (jdbc/execute! db/*db*
                     (cons (str "UPDATE c_participantadministration SET Date = 0 WHERE assessment IN (" qmarks ")")
                           assessment-ids))
      (jdbc/execute! db/*db*
                     (cons (str "UPDATE c_groupadministration SET Date = 0 WHERE assessment IN (" qmarks ")")
                           assessment-ids))))

(defn clear-administrations!
  []
  (jdbc/execute! db/*db*
                 (cons (str "UPDATE c_participantadministration SET Date = 0 WHERE ParentInterface IN(?, ?)")
                       [project-ass1-id project-ass2-id]))
  (jdbc/execute! db/*db*
                 (cons (str "UPDATE c_groupadministration SET Date = 0 WHERE ParentInterface IN (?, ?)")
                       [project-ass1-id project-ass2-id])))

(def ^:dynamic *now*)
(def ^:dynamic *tz*)

(defn midnight-joda
  [now]
  (-> now
      (t/to-time-zone *tz*)
      (t/with-time-at-start-of-day)))

(defn midnight
  [now]
  (-> now
      (t/to-time-zone *tz*)
      (t/with-time-at-start-of-day)
      (utils/to-unix)))

(defn midnight+d
  [plus-days now]
  (utils/to-unix (-> (t/plus now
                             (t/days plus-days))
                     (t/to-time-zone *tz*)
                     (t/with-time-at-start-of-day))))

(defn random-date
  []
  (-> (long (rand 2147483647))
      (tc/from-epoch)))

(defn random-date-tz-fixture
  [f]
  (clear-administrations!)
  (let [tz  (t/time-zone-for-id (rand-nth (seq (t/available-ids))))
        now (random-date)]
    (binding [*tz*  tz
              *now* now]
      (f))))

(defn reminders
  [now]
  (->> (assessment-reminder/reminders db/*db* now *tz*)
       (map #(vector
               (:user-id %)
               (some? (:participant-administration-id %))
               (:assessment-id %)
               (:assessment-index %)
               (::assessment-reminder/remind-type %)
               (::assessment-reminder/remind-number %)))
       (into #{})))

(defn remind!
  [now]
  (assessment-reminder/remind! db/*db* now *tz* (constantly true) (constantly true)))

(defn remind!-administrations-created
  [now]
  (let [c (chan)]
    (binding [missing/*create-count-chan* c]
      (remind! now)
      (let [[create-count _] (alts!! [c (timeout 1000)])]
        create-count))))

(defn remind!-quick-logins-created
  [now]
  (let [c (chan)]
    (binding [quick-login/*quick-login-updates-chan* c]
      (remind! now)
      (let [[quick-login-user-ids _] (alts!! [c (timeout 1000)])]
        quick-login-user-ids))))

(defn remind!-messages-sent
  [now]
  (let [email-chan (chan)
        sms-chan   (chan)]
    (assessment-reminder/remind! db/*db* now *tz* #(put! email-chan %) #(put! sms-chan %))
    (let [[emails _] (alts!! [email-chan (timeout 1000)])
          [smses _] (alts!! [sms-chan (timeout 1000)])]
      (into #{} (concat (map #(vector (:user-id %) :email (:to %) (:subject %) (:message %)) emails)
                        (map #(vector (:user-id %) :sms (:to %) (:message %)) smses))))))

(defn messages
  [now]
  (->> (assessment-reminder/reminders db/*db* now *tz*)
       (assessment-reminder/message-assessments)
       (map #(vector
               (:user-id %)
               (:assessment-id %)
               (::assessment-reminder/message-type %)))
       (into #{})))

(defn group-statuses
  [now group-id]
  (into #{} (map #(vector (:assessment-id %) (:assessment-index %) (:status %))
                 (assessment-statuses/group-administrations-statuses db/*db* now group-id))))

(defn user-statuses
  [now user-id]
  (into #{} (map #(utils/select-values % [:assessment-id
                                          :assessment-index
                                          :status])
                 (assessment-statuses/user-administrations-statuses db/*db* now user-id))))

(defn flag!-flags-created
  ([now] (flag!-flags-created now nil nil))
  ([now created-atom flag-count]
   (into #{} (map #(utils/select-values % [:user-id
                                           :assessment-id
                                           :assessment-index])
                  (if created-atom
                    (let [c   (chan flag-count)
                          res (binding [assessment-flagger/*create-flag-chan* c]
                                (assessment-flagger/flag-late-assessments! *db* now))]
                      (dotimes [n flag-count]
                        (let [[[user-id flag-id] _] (alts!! [c (timeout 1000)])]
                          (when (nil? user-id)
                            (throw (Exception. (str "Flag " n " timed out"))))
                          (utils/swap-key!
                            created-atom
                            user-id
                            #(conj % flag-id)
                            [])))
                      res)
                    (assessment-flagger/flag-late-assessments! *db* now))))))

(defn flag-comment-count
  [flag-id]
  (-> (jdbc/query *db* ["SELECT count(*) FROM c_comment WHERE ParentId = ?" flag-id])
      (first)
      (vals)
      (first)))