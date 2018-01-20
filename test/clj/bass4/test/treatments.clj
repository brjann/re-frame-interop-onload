(ns bass4.test.treatments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.assessments :as assessments]
            [bass4.services.treatment :as treatment :refer [treatment-active?]]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [bass4.services.user :as user]
            [bass4.services.bass :as bass]
            [clj-time.coerce :as tc]))

(use-fixtures
  :once
  test-fixtures)

;; TODO: It's not possible to test :modules-automatic-access because BASS messes it up
(deftest two-modules
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    (let [treatments       (treatment/user-treatment 543021)
          treatment-access (:treatment-access treatments)]
      (is (= 3958 (:treatment-id treatment-access)))
      (is (= #{5787 3961} (into #{} (map :module-id (filter :active (get-in treatments [:user-components :modules])))))))))


(deftest auto-modules-test
  (let [user-id             (user/create-user! 543018 {:Group "537404" :firstname "autotest-module"})
        treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                :parent-id     user-id
                                                                :property-name "TreatmentAccesses"}))]
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     551356
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    (let [user-treatment (treatment/user-treatment user-id)]
      (is (= #{5787 4002 4003 4007} (into #{} (map :module-id (filter :active (get-in user-treatment [:user-components :modules])))))))))
;	public function getRemainingTreatmentDuration(){
;		if($this->Treatment->AccessStartAndEndDate){
;			if(getMidnight() < $this->StartDate) return 0;
;			return getDateSpan(getMidnight(), $this->EndDate);
;		}
;		if(!$this->Treatment->AccessEnablingRequired) return 1;
;		if($this->Treatment->AccessEnablingRequired) return (int)$this->AccessEnabled;
;		return 0;
;	}

(deftest treatment-active-tests
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (is (= false (treatment-active? {:start-date     (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                     :end-date       (tc/from-date #inst"2017-06-10T23:00:00.000000000-00:00")
                                     :access-enabled true}
                                    {:access-time-limited true})))
    (is (= true (treatment-active? {:start-date     (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date       (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled false}
                                   {:access-time-limited true})))
    (is (= true (treatment-active? {:start-date     (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date       (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled false}
                                   {:access-time-limited      true
                                    :access-enabling-required true})))
    (is (= false (treatment-active? {:start-date (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                     :end-date   (tc/from-date #inst"2017-10-17T23:00:00.000000000-00:00")}
                                    {:access-time-limited true})))
    (is (= true (treatment-active? {:start-date (tc/from-date #inst"2007-06-17T23:00:00.000000000-00:00")
                                    :end-date   (tc/from-date #inst"2007-10-17T23:00:00.000000000-00:00")}
                                   {:access-enabling-required false})))
    (is (= false (treatment-active? {:start-date     (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                     :end-date       (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                     :access-enabled false}
                                    {:access-enabling-required true})))
    (is (= true (treatment-active? {:start-date     (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date       (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled true}
                                   {:access-enabling-required true})))))

(deftest treatment-multiple-active
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    ;; First and second treatment active
    (is (= 3958 (get-in (treatment/user-treatment 549821) [:treatment :treatment-id])))
    ;; First inactive second active
    (is (= 3972 (get-in (treatment/user-treatment 550132) [:treatment :treatment-id])))))

(deftest treatment-messaging
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    ;; User not allowed - treatment allows
    (is (= false (get-in (treatment/user-treatment 549821) [:user-components :send-messages])))
    ;; User allowed - treatment allows
    (is (= true (get-in (treatment/user-treatment 543021) [:user-components :send-messages])))
    ;; User allowed - treatment does not allows
    (is (= false (get-in (treatment/user-treatment 550132) [:user-components :send-messages])))))