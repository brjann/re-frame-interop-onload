(ns bass4.test.treatments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [clj-time.coerce :as tc]
            [bass4.module.services :as module-service]))

(use-fixtures
  :once
  test-fixtures)

;; TODO: It's not possible to test :modules-automatic-access because BASS messes it up
(deftest two-modules
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    (let [treatments       (treatment-builder/user-treatment 543021)
          treatment-access (:treatment-access treatments)]
      (is (= 3958 (:treatment-id treatment-access)))
      (is (= #{5787 3961} (into #{} (map :module-id (filter :active? (get-in treatments [:tx-components :modules])))))))))

(deftest empty-content
  (let [module (module-service/module-contents [3961])]
    (is (= #{3980 4001 3989}
           (->>
             module
             (into #{} (map :content-id)))))))


(deftest auto-modules-test
  (let [user-id             (user-service/create-user! 543018 {:Group "537404" :firstname "autotest-module"})
        treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                :parent-id     user-id
                                                                :property-name "TreatmentAccesses"}))]
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     551356
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    (let [user-treatment (treatment-builder/user-treatment user-id)]
      (is (= #{5787 4002 4003 4007} (into #{} (map :module-id (filter :active? (get-in user-treatment [:tx-components :modules])))))))))

(deftest treatment-active-tests
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (is (= false (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                     :end-date                     (tc/from-date #inst"2017-06-10T23:00:00.000000000-00:00")
                                     :access-enabled?              true}
                                                      {:access-time-limited? true})))
    (is (= true (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date                     (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled?              false}
                                                     {:access-time-limited? true})))
    (is (= true (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date                     (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled?              false}
                                                     {:access-time-limited?      true
                                    :access-enabling-required? true})))
    (is (= false (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                     :end-date                     (tc/from-date #inst"2017-10-17T23:00:00.000000000-00:00")}
                                                      {:access-time-limited? true})))
    (is (= true (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2007-06-17T23:00:00.000000000-00:00")
                                    :end-date                     (tc/from-date #inst"2007-10-17T23:00:00.000000000-00:00")}
                                                     {:access-enabling-required? false})))
    (is (= false (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                     :end-date                     (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                     :access-enabled?              false}
                                                      {:access-enabling-required? true})))
    (is (= true (treatment-builder/treatment-active? {:start-date (tc/from-date #inst"2017-02-17T23:00:00.000000000-00:00")
                                    :end-date                     (tc/from-date #inst"2017-06-17T23:00:00.000000000-00:00")
                                    :access-enabled?              true}
                                                     {:access-enabling-required? true})))))

(deftest treatment-multiple-active
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    ;; First and second treatment active
    (is (= 3958 (get-in (treatment-builder/user-treatment 549821) [:treatment :treatment-id])))
    ;; First inactive second active
    (is (= 3972 (get-in (treatment-builder/user-treatment 550132) [:treatment :treatment-id])))))

(deftest treatment-messaging
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    ;; User not allowed - treatment allows
    (is (= false (get-in (treatment-builder/user-treatment 549821) [:tx-components :send-messages?])))
    ;; User allowed - treatment allows
    (is (= true (get-in (treatment-builder/user-treatment 543021) [:tx-components :send-messages?])))
    ;; User allowed - treatment does not allows
    (is (= false (get-in (treatment-builder/user-treatment 550132) [:tx-components :send-messages?])))))