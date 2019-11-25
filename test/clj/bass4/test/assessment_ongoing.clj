(ns bass4.test.assessment-ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(def custom-participant-id 654412)
(def custom-administration-id 654430)
(def custom-assessment-id 654429)

(defn ongoing-assessments
  [now user-id]
  (let [res (assessment-ongoing/ongoing-assessments* db/*db* now user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-G-s-2-3-p0 1] [ass-G-week-e+s-3-4-p10 4]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]
             [ass-G-week-e+s-3-4-p10 4 ::assessment-ongoing/as-ongoing]
             [ass-G-week-e+s-3-4-p10 1 ::assessment-ongoing/as-waiting]}
           (participant-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]
             [ass-G-week-e+s-3-4-p10 4 ::assessment-ongoing/as-ongoing]
             [ass-G-week-e+s-3-4-p10 1 ::assessment-ongoing/as-waiting]}
           (group-statuses *now* group-id)))))

(deftest group-assessment-mysql-old-super-join-fail
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 3 {:date (midnight+d -7 *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-G-week-e+s-3-4-p10 3)
    (is (= #{[ass-G-week-e+s-3-4-p10 4]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-week-e+s-3-4-p10 3 ::assessment-ongoing/as-date-passed]
             [ass-G-week-e+s-3-4-p10 4 ::assessment-ongoing/as-ongoing]}
           (participant-statuses *now* user-id)))
    (is (= #{[ass-G-week-e+s-3-4-p10 3 ::assessment-ongoing/as-date-passed]
             [ass-G-week-e+s-3-4-p10 4 ::assessment-ongoing/as-ongoing]}
           (group-statuses *now* group-id)))))

(deftest group-assessment-timelimit
  ; Timelimit within
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-G-s-2-3-p0 1]} (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]}
           (participant-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]}
           (group-statuses *now* group-id))))

  ; Timelimit too late
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight+d -40 *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-date-passed]}
           (participant-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-date-passed]}
           (group-statuses *now* group-id)))))

(deftest individual-assessment-in-group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    ; Today
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-I-week-noremind 1 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-I-week-noremind 4 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-I-s-0-p100-message 1]
             [ass-I-week-noremind 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-I-s-0-p100-message 1 ::assessment-ongoing/as-ongoing]
             [ass-I-week-noremind 1 ::assessment-ongoing/as-ongoing]
             [ass-I-week-noremind 4 ::assessment-ongoing/as-waiting]}
           (participant-statuses *now* user-id)))
    (is (= #{}
           (group-statuses *now* group-id)))))

(deftest individual-assessment-no-group
  (let [user-id (user-service/create-user! project-ass1-id)]
    ; Today
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-I-week-noremind 1 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-I-week-noremind 4 {:date (midnight+d 1 *now*)})
    (is (= #{[ass-I-s-0-p100-message 1]
             [ass-I-week-noremind 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-I-s-0-p100-message 1 ::assessment-ongoing/as-ongoing]
             [ass-I-week-noremind 1 ::assessment-ongoing/as-ongoing]
             [ass-I-week-noremind 4 ::assessment-ongoing/as-waiting]}
           (participant-statuses *now* user-id)))))

(deftest individual+group-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1)
    (is (= #{[ass-I-s-0-p100-message 1]
             [ass-G-s-2-3-p0 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-I-s-0-p100-message 1 ::assessment-ongoing/as-ongoing]
             [ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]}
           (participant-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 ::assessment-ongoing/as-ongoing]}
           (group-statuses *now* group-id)))))

(deftest index-overflow-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-participant-administration!
      user-id ass-I-week-noremind 5 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 5 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual-assessment-timelimit
  ; Timelimit within
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-I-s-0-p100-message 1]} (ongoing-assessments *now* user-id))))

  ; Timelimit too late
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight+d -4 *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual+group-inactive-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1 {:active 0})
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-G-s-2-3-p0 1 {:active 0})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest manual-assessment
  ;; Create administrations in reverse order to check sorting of them
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-I-manual-s-5-10-q 1]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -3 *now*)})
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -2 *now*)})
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 3 {:date (midnight+d -1 *now*)})
    ; Only last assessment active
    (is (= #{[ass-I-manual-s-5-10-q 3]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -3 *now*)})
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -2 *now*)})
    ; Only last assessment active - even if it has lower start date
    (is (= #{[ass-I-manual-s-5-10-q 2]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
    ; Only last assessment active - even if one is skipped
    (is (= #{[ass-I-manual-s-5-10-q 3]} (ongoing-assessments *now* user-id))))

  ; Later inactive assessment does not inactivate manual assessment
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*) :active 0})
    ; First assessment active - even later is inactive
    (is (= #{[ass-I-manual-s-5-10-q 1]} (ongoing-assessments *now* user-id))))

  ; Later inactive group assessment does not inactivate manual assessment
  (let [group1-id (create-group!)
        user1-id  (user-service/create-user! project-ass1-id {:group group1-id})]
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-I-manual-s-5-10-q 4 {:active 0})
    (is (= #{[ass-I-manual-s-5-10-q 2]} (ongoing-assessments *now* user1-id)))))

(deftest clinician-assessment
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-clinician 1 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest start-hour-assessment
  (let [user-id (user-service/create-user! project-ass1-id)]
    (create-participant-administration!
      user-id ass-I-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (ongoing-assessments hour0 user-id))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (ongoing-assessments hour7 user-id))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[ass-I-hour8-2-20 1]}
             (ongoing-assessments hour8 user-id))))
    (let [tomorrow (t/plus (midnight-joda *now*) (t/days 1))]
      (is (= #{[ass-I-hour8-2-20 1]} (ongoing-assessments tomorrow user-id))))))

(deftest no-administrations
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest unlinked-administration
  (let [user-id (user-service/create-user! project-ass1-id)]
    ; Today
    (create-participant-administration!
      user-id 666 1 {:date (midnight *now*)})
    ;; Does not crash
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest change-project
  (let [user-id (user-service/create-user! project-ass2-id)
        adm1-id (create-participant-administration!
                  user-id p2-ass-I1 1 {:date (midnight *now*)})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (is (= #{[p2-ass-I1 1]} (ongoing-assessments *now* user-id)))
    (bass/update-object-properties! "c_participant" user-id {"parentid"        project-ass2-pcollection-id
                                                             "parentinterface" project-ass1-id})
    (bass/update-object-properties! "c_participantadministration" adm1-id {"parentinterface" project-ass1-id})
    (is (= #{[ass-I-s-0-p100-message 1]} (ongoing-assessments *now* user-id)))
    (participant-statuses *now* user-id)))

(deftest custom-assessment
  (db/update-object-properties! {:table-name "c_participantadministration"
                                 :object-id  custom-administration-id
                                 :updates    {:date (midnight *now*)}})
  (is (= #{[custom-assessment-id 1]}
         (ongoing-assessments *now* custom-participant-id))))

(deftest full-return-assessment-group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      #_(is (= #{:user-id
                 :thank-you-text
                 :repetition-type
                 :assessment-index
                 :show-texts-if-swallowed?
                 :date-completed
                 :assessment-id
                 :participant-activation-date
                 :repetition-interval
                 :scope
                 :instruments
                 :welcome-text
                 :shuffle-instruments
                 :priority
                 :group-administration-id
                 :participant-administration-active?
                 :group-administration-active?
                 :active?
                 :status
                 :clinician-rated?
                 :repetitions
                 :group-activation-date
                 :time-limit
                 :is-record?
                 :participant-administration-id
                 :allow-swallow?
                 :assessment-name
                 :activation-hour}
               (into #{} (keys res))))
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou"
                             :welcome-text   "welcome"
                             :instruments    [4743 286]}
                            res))))))

(deftest full-return-assessment-individual-assessment
  (let [user-id (user-service/create-user! project-ass1-id)]
    ; Today
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      #_(is (= #{:user-id
                 :thank-you-text
                 :repetition-type
                 :assessment-index
                 :show-texts-if-swallowed?
                 :date-completed
                 :assessment-id
                 :participant-activation-date
                 :repetition-interval
                 :scope
                 :instruments
                 :welcome-text
                 :shuffle-instruments
                 :priority
                 :group-administration-id
                 :active?
                 :status
                 :clinician-rated?
                 :repetitions
                 :group-activation-date
                 :time-limit
                 :is-record?
                 :participant-administration-id
                 :allow-swallow?
                 :assessment-name
                 :activation-hour}
               (into #{} (keys res))))
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou1"
                             :welcome-text   "welcome1"
                             ; Clinician instrument removed
                             :instruments    [4458 1609]}
                            res))))))