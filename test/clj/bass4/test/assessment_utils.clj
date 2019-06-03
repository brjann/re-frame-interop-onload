(ns bass4.test.assessment-utils
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.bass :as bass]
            [bass4.utils :as utils]))

(use-fixtures
  :once
  test-fixtures)

(defn midnight
  ([] (midnight (t/now)))
  ([now] (utils/to-unix (bass/local-midnight now))))

(defn midnight+d
  ([plus-days] (midnight+d plus-days (t/now)))
  ([plus-days now]
   (utils/to-unix (t/plus (bass/local-midnight now) (t/days plus-days)))))

(def project-id 653627)
(def group-single 653630)

(def ass-group-weekly 653631)
(def ass-individual-single 653632)
(def ass-individual-weekly 653633)
(def ass-individual-manual-repeat 653634)
(def ass-clinician 654215)
(def ass-hour-8 654411)

(def assessment-ids [ass-group-weekly
                     ass-individual-single
                     ass-individual-weekly
                     ass-individual-manual-repeat
                     ass-clinician
                     ass-hour-8])

(defn create-group!
  []
  (:objectid (db/create-bass-object! {:class-name    "cGroup"
                                      :parent-id     project-id
                                      :property-name "Groups"})))

(defn create-group-administration!
  [group-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cGroupAdministration"
                                                              :parent-id     group-id
                                                              :property-name "Administrations"}))]
    (when properties
      (db/update-object-properties! {:table-name "c_groupadministration"
                                     :object-id  administration-id
                                     :updates    (merge {:assessment      assessment-id
                                                         :assessmentindex assessment-index
                                                         :active          1}
                                                        properties)}))))

(defn create-participant-administration!
  [user-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cParticipantAdministration"
                                                              :parent-id     user-id
                                                              :property-name "Administrations"}))]
    (when properties
      (db/update-object-properties! {:table-name "c_participantadministration"
                                     :object-id  administration-id
                                     :updates    (merge {:assessment      assessment-id
                                                         :assessmentindex assessment-index
                                                         :active          1
                                                         :deleted         0}
                                                        properties)}))))