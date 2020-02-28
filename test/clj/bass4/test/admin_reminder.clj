(ns ^:eftest/synchronized
  bass4.test.admin-reminder
  (:require [clj-time.core :as t]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.task.admin-reminder :as admin-reminder]
            [bass4.services.user :as user-service]
            [bass4.test.assessment-utils :refer [project-ass1-id project-ass2-id]]
            [bass4.db.core :as db]
            [bass4.services.messages :as messages]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [bass4.services.bass :as bass-service]
            [clj-time.coerce :as tc]
            [bass4.module.services :as module-service]
            [bass4.db.orm-classes :as orm]))

(use-fixtures
  :once
  test-fixtures)

(defn clear-flags!
  []
  (jdbc/execute! db/*db* ["TRUNCATE c_flag"]))

(defn clear-messages!
  []
  (jdbc/execute! db/*db* ["TRUNCATE c_message"]))

(defn clear-homework!
  []
  (jdbc/execute! db/*db* ["TRUNCATE content_data_homework"]))

(defn -ms
  [time]
  (let [ms (t/milli time)]
    (t/minus time (t/millis ms))))

;; ------------------------
;;     UNREAD MESSAGES
;; ------------------------

(defn set-message-properties
  [message-id send-time read?]
  (orm/update-object-properties*! db/*db*
                                  "c_message"
                                  message-id
                                  {"SendTime" (utils/to-unix send-time)
                                   "ReadTime" (if read? 1 0)
                                   "Draft"    0}))

(defn check-messages
  [time-limit]
  (let [res (admin-reminder/db-unread-messages db/*db* time-limit)]
    (->> res
         (map (juxt :user-id :time :count))
         (into #{}))))

(deftest unread-messages
  (clear-messages!)
  (let [user1-id   (user-service/create-user! project-ass1-id)
        user2-id   (user-service/create-user! project-ass1-id)
        user3-id   (user-service/create-user! project-ass1-id)
        message1-1 (messages/create-message-placeholder user1-id)
        message1-2 (messages/create-message-placeholder user1-id)
        message2-1 (messages/create-message-placeholder user2-id)
        message2-2 (messages/create-message-placeholder user2-id)
        message3-1 (messages/create-message-placeholder user3-id)
        message3-2 (messages/create-message-placeholder user3-id)
        t60days    (-ms (t/minus (t/now) (t/days 60)))
        t45days    (-ms (t/minus (t/now) (t/days 45)))
        now        (-ms (t/now))]
    (set-message-properties message1-1 t60days false)
    (set-message-properties message1-2 now false)
    (set-message-properties message2-1 now true)
    (set-message-properties message2-2 t60days false)
    (set-message-properties message3-1 now false)
    (set-message-properties message3-2 t45days false)
    (is (= now (tc/from-epoch (utils/to-unix now))))
    (is (= #{[user1-id now 1]
             [user3-id t45days 2]}
           (check-messages t60days)))))


;; ------------------------
;;        OPEN FLAGS
;; ------------------------

(defn create-flag!
  ([db user-id created-time open?]
   (create-flag! db user-id created-time open? ""))
  ([db user-id created-time open? issuer]
   (let [flag-id (orm/create-bass-object*! db
                                           "cFlag"
                                           user-id
                                           "Flags")]
     (orm/update-object-properties*! db
                                     "c_flag"
                                     flag-id
                                     {"Created"  (utils/to-unix created-time)
                                      "ParentId" user-id
                                      "Issuer"   issuer
                                      "Open"     (if open? 1 0)
                                      "ClosedAt" (if open? 0 1)}))))

(defn check-open-flags
  [time-limit]
  (let [res (admin-reminder/db-open-flags db/*db* time-limit)]
    (->> res
         (map (juxt :user-id :time :count))
         (into #{}))))

(deftest open-flags
  (clear-flags!)
  (let [user1-id (user-service/create-user! project-ass1-id)
        user2-id (user-service/create-user! project-ass1-id)
        user3-id (user-service/create-user! project-ass1-id)
        t60days  (-ms (t/minus (t/now) (t/days 60)))
        t45days  (-ms (t/minus (t/now) (t/days 45)))
        now      (-ms (t/now))]
    (create-flag! db/*db* user1-id t60days true)
    (create-flag! db/*db* user1-id now true)
    (create-flag! db/*db* user2-id t60days true)
    (create-flag! db/*db* user2-id now false)
    (create-flag! db/*db* user3-id t45days true)
    (create-flag! db/*db* user3-id now true)
    (is (= #{[user1-id now 1]
             [user3-id t45days 2]}
           (check-open-flags t60days)))))

;; ------------------------
;;      PASSWORD FLAGS
;; ------------------------


(defn create-password-flag!
  [db user-id created-time open?]
  (create-flag! db user-id created-time open? "lost-password"))

(defn check-password-flags
  [time-limit]
  (let [res (admin-reminder/db-lost-password-flags db/*db* time-limit)]
    (->> res
         (map (juxt :user-id :time :count))
         (into #{}))))

(deftest password-flags
  (clear-flags!)
  (let [user1-id (user-service/create-user! project-ass1-id)
        user2-id (user-service/create-user! project-ass1-id)
        user3-id (user-service/create-user! project-ass1-id)
        user4-id (user-service/create-user! project-ass1-id)
        t60days  (-ms (t/minus (t/now) (t/days 60)))
        t45days  (-ms (t/minus (t/now) (t/days 45)))
        now      (-ms (t/now))]
    (create-password-flag! db/*db* user1-id t60days true)
    (create-password-flag! db/*db* user1-id now true)
    (create-password-flag! db/*db* user2-id t60days true)
    (create-password-flag! db/*db* user2-id now false)
    (create-password-flag! db/*db* user3-id t45days true)
    (create-password-flag! db/*db* user3-id now true)
    (create-flag! db/*db* user4-id t45days true)
    (is (= #{[user1-id now 1]
             [user3-id t45days 2]}
           (check-password-flags t60days)))))

;; ------------------------
;;      UNREAD HOMEWORK
;; ------------------------

(defn create-treatment-access!
  [user-id]
  (let [treatment-access-id (orm/create-bass-object*! db/*db*
                                                      "cTreatmentAccess"
                                                      user-id
                                                      "TreatmentAccesses")]
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     642517
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    treatment-access-id))

(defn submit-homework!
  [treatment-access-id time-sent one-or-two]
  (module-service/submit-homework! treatment-access-id
                                   {:module-id (case one-or-two
                                                 1 642518
                                                 2 642529)}
                                   time-sent))

(defn check-homework
  [time-limit]
  (let [res (admin-reminder/db-unread-homework db/*db* time-limit)]
    (->> res
         (map (juxt :user-id :time :count))
         (into #{}))))

(deftest unread-homework
  (clear-homework!)
  (let [user1-id     (user-service/create-user! project-ass1-id)
        user2-id     (user-service/create-user! project-ass1-id)
        user3-id     (user-service/create-user! project-ass1-id)
        t-access1-id (create-treatment-access! user1-id)
        t-access2-id (create-treatment-access! user2-id)
        t-access3-id (create-treatment-access! user3-id)
        t60days      (-ms (t/minus (t/now) (t/days 60)))
        t45days      (-ms (t/minus (t/now) (t/days 45)))
        now          (-ms (t/now))]
    (submit-homework! t-access1-id t60days 1)
    (submit-homework! t-access1-id now 1)
    (submit-homework! t-access2-id t60days 1)
    (submit-homework! t-access3-id t45days 1)
    (submit-homework! t-access3-id now 2)
    (is (= #{[user1-id now 1]
             [user3-id t45days 2]}
           (check-homework t60days)))
    (try
      (orm/update-object-properties*! db/*db*
                                      "c_treatment"
                                      642517
                                      {"UseHomeworkInspection" 0})
      (is (= #{}
             (check-homework t60days)))
      (finally
        (orm/update-object-properties*! db/*db*
                                        "c_treatment"
                                        642517
                                        {"UseHomeworkInspection" 1})))))

;; ------------------------
;;        CHECK ALL
;; ------------------------

(def therapist1-id 1562)
(def therapist2-id 1955)

(defn link-to-therapist!
  [participant-id therapist-id]
  (db/create-bass-link! {:linker-id     therapist-id
                         :linkee-id     participant-id
                         :link-property "MyParticipants"
                         :linker-class  "cTherapist"
                         :linkee-class  "cParticipant"}))

(defn check-all
  [time-limit]
  (->> (admin-reminder/collect-reminders db/*db* time-limit)
       (utils/map-map (fn [l] (->> l
                                   (map (juxt :type :time :count))
                                   (into #{}))))))

(deftest collect-all
  (clear-flags!)
  (clear-messages!)
  (clear-homework!)
  (let [user1-id     (user-service/create-user! project-ass1-id)
        user2-id     (user-service/create-user! project-ass1-id)
        user3-id     (user-service/create-user! project-ass1-id)
        user4-id     (user-service/create-user! project-ass1-id)
        user5-id     (user-service/create-user! project-ass2-id)
        t-access1-id (create-treatment-access! user1-id)
        t-access2-id (create-treatment-access! user2-id)
        message1     (messages/create-message-placeholder user1-id)
        message4     (messages/create-message-placeholder user4-id)
        t60days      (-ms (t/minus (t/now) (t/days 60)))
        t45days      (-ms (t/minus (t/now) (t/days 45)))
        now          (-ms (t/now))]
    (link-to-therapist! user1-id therapist1-id)
    (link-to-therapist! user1-id therapist2-id)
    (link-to-therapist! user2-id therapist1-id)
    (link-to-therapist! user3-id therapist2-id)
    (submit-homework! t-access1-id now 1)
    (submit-homework! t-access1-id t45days 2)
    (submit-homework! t-access2-id t45days 1)
    (create-password-flag! db/*db* user3-id now true)
    (create-password-flag! db/*db* user4-id t45days true)
    (create-flag! db/*db* user2-id now true)
    (create-flag! db/*db* user3-id t45days true)
    (create-flag! db/*db* user5-id t45days true)
    (set-message-properties message1 t45days false)
    (set-message-properties message4 t45days false)
    (is (= {user1-id #{[::admin-reminder/unread-homework t45days 2]
                       [::admin-reminder/unread-messages t45days 1]}
            user2-id #{[::admin-reminder/open-flags now 1]
                       [::admin-reminder/unread-homework t45days 1]}
            user3-id #{[::admin-reminder/password-flags now 1]
                       [::admin-reminder/open-flags t45days 2]}
            user4-id #{[::admin-reminder/password-flags t45days 1]
                       [::admin-reminder/open-flags t45days 1]
                       [::admin-reminder/unread-messages t45days 1]}
            user5-id #{[::admin-reminder/open-flags t45days 1]}}
           (check-all t60days)))
    (is (= [{[therapist1-id "therapist1@bass4.com"] #{user1-id user2-id}
             [therapist2-id "therapist2@bass4.com"] #{user1-id user3-id}}
            {[0 "project@bass4.com"] #{user4-id user5-id}}]
           (let [reminders-by-participants (admin-reminder/collect-reminders db/*db*
                                                                             t60days)
                 participant-ids           (into #{} (keys reminders-by-participants))
                 therapists                (admin-reminder/db-therapists db/*db* participant-ids)]
             [(admin-reminder/collapse-therapists therapists)
              (admin-reminder/projects-for-participants db/*db* participant-ids therapists)])))))