(ns bass4.test.assessment-utils
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [chan alts!! timeout put! <! go-loop]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as tc]
            [bass4.utils :as utils]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.create-missing :as missing]
            [bass4.routes.quick-login :as quick-login]
            [bass4.services.bass :as bass]
            [bass4.assessment.statuses :as assessment-statuses]
            [clojure.tools.logging :as log]
            [bass4.assessment.late-flagger :as late-flagger]
            [bass4.assessment.activated-flagger :as activated-flagger]
            [bass4.db.orm-classes :as orm]
            [bass4.assessment.db :as assessment-db]))

#_(use-fixtures
    :once
    test-fixtures)

(def project-ass1-id 653627)
(def project-ass1-pcollection-id 653628)
(def project-ass2-id 658098)
(def project-ass2-assessment-series 658100)
(def project-ass2-pcollection-id 658099)

(def ass-flag-assessment-series 653629)

(defn create-group!
  ([] (create-group! project-ass1-id))
  ([project-id]
   (:objectid (orm/create-bass-object-map! {:class-name    "cGroup"
                                            :parent-id     project-id
                                            :property-name "Groups"}))))

(defn create-assessment!
  ([properties] (create-assessment! ass-flag-assessment-series properties))
  ([project-id properties]
   (let [assessment-id (:objectid (orm/create-bass-object-map! {:class-name    "cAssessment"
                                                                :parent-id     project-id
                                                                :property-name "Assessments"}))]
     (orm/update-object-properties! "c_assessment"
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
     assessment-id)))

(defn link-instrument!
  [assessment-id instrument-id]
  (orm/create-link! assessment-id instrument-id "Instruments" "cAssessment" "cInstrument"))

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
  (let [administration-id (:objectid (orm/create-bass-object-map! {:class-name    "cGroupAdministration"
                                                                   :parent-id     group-id
                                                                   :property-name "Administrations"}))]
    (when properties
      (orm/update-object-properties! "c_groupadministration"
                                     administration-id
                                     (merge {"assessment"      assessment-id
                                             "assessmentindex" assessment-index
                                             "active"          1}
                                            properties)))
    administration-id))

(defn create-participant-administration!
  [user-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (orm/create-bass-object-map! {:class-name    "cParticipantAdministration"
                                                                   :parent-id     user-id
                                                                   :property-name "Administrations"}))]
    (orm/update-object-properties! "c_participantadministration"
                                   administration-id
                                   (merge {"assessment"      assessment-id
                                           "assessmentindex" assessment-index
                                           "active"          1
                                           "deleted"         0}
                                          properties))
    administration-id))

(defn create-custom-assessment*!
  [user-id]
  (let [assessment-id (:objectid (orm/create-bass-object-map! {:class-name    "cAssessment"
                                                               :parent-id     user-id
                                                               :property-name "AdHocAssessments"}))]
    (orm/update-object-properties! "c_assessment"
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
    (additional-instruments! administration-id instrument-ids)
    assessment-id))

(defn clear-administrations!
  []
  (jdbc/execute! db/*db*
                 (cons (str "UPDATE c_participantadministration SET Date = 0 WHERE ParentInterface IN(?, ?)")
                       [project-ass1-id project-ass2-id]))
  (jdbc/execute! db/*db*
                 (cons (str "UPDATE c_groupadministration SET Date = 0 WHERE ParentInterface IN (?, ?)")
                       [project-ass1-id project-ass2-id]))
  (jdbc/execute! db/*db* [(str "TRUNCATE c_flag")]))

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

(defn random-date-tz-fixture-new
  [f]
  #_(clear-administrations!)
  (let [tz  (t/time-zone-for-id (rand-nth (seq (t/available-ids))))
        now (random-date)]
    (binding [*tz*  tz
              *now* now]
      (f))))


(defn filter-created-objects-fixture
  [f]
  (let [bind-fn                      (fn [f key]
                                       (fn [& args]
                                         (let [res      (apply f args)
                                               filtered (filter #(contains? @orm/*created-objects* (get % key))
                                                                res)]
                                           filtered)))
        activated-remind-participant assessment-db/potential-activated-remind-participant-administrations
        activated-remind-group       assessment-db/potential-activated-remind-group-administrations
        late-remind-participant      assessment-db/potential-late-remind-participant-administrations
        late-remind-group            assessment-db/potential-late-remind-group-administrations
        activated-flag-participant   assessment-db/potential-activated-flag-participant-administrations
        activated-flag-group         assessment-db/potential-activated-flag-group-administrations
        late-flag-participant        assessment-db/potential-late-flag-participant-administrations
        late-flag-group              assessment-db/potential-late-flag-group-administrations
        open-flags                   assessment-db/open-late-administration-flags]
    (binding [orm/*created-objects* (atom #{})
              assessment-db/potential-activated-remind-participant-administrations
                                    (bind-fn activated-remind-participant :participant-administration-id)
              assessment-db/potential-activated-remind-group-administrations
                                    (bind-fn activated-remind-group :group-administration-id)
              assessment-db/potential-late-remind-participant-administrations
                                    (bind-fn late-remind-participant :participant-administration-id)
              assessment-db/potential-late-remind-group-administrations
                                    (bind-fn late-remind-group :group-administration-id)
              assessment-db/potential-activated-flag-participant-administrations
                                    (bind-fn activated-flag-participant :participant-administration-id)
              assessment-db/potential-activated-flag-group-administrations
                                    (bind-fn activated-flag-group :group-administration-id)
              assessment-db/potential-late-flag-participant-administrations
                                    (bind-fn late-flag-participant :participant-administration-id)
              assessment-db/potential-late-flag-group-administrations
                                    (bind-fn late-flag-group :group-administration-id)
              assessment-db/open-late-administration-flags
                                    (bind-fn open-flags :participant-administration-id)]
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

(defn flag-late!
  ([now] (flag-late! now nil nil))
  ([now created-atom flag-count]
   (into #{} (map #(utils/select-values % [:user-id
                                           :assessment-id
                                           :assessment-index])
                  (if created-atom
                    (let [c   (chan flag-count)
                          res (binding [late-flagger/*create-flag-chan* c]
                                (late-flagger/flag-assessments! *db* now))]
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
                    (late-flagger/flag-assessments! *db* now))))))

(defn flag-activated!
  ([now]
   (into #{} (map #(utils/select-values % [:user-id
                                           :assessment-id
                                           :assessment-index])
                  (activated-flagger/flag-assessments! *db* now *tz*)))))

(defn flag-comment-count
  [flag-id]
  (-> (jdbc/query *db* ["SELECT count(*) FROM c_comment WHERE ParentId = ?" flag-id])
      (first)
      (vals)
      (first)))