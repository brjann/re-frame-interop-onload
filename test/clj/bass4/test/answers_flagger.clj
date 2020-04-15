(ns bass4.test.answers-flagger
  (:require [clojure.test :refer :all]
            [bass4.instrument.flagger :as answers-flagger]
            [bass4.instrument.services :as instruments]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.infix-parser :as infix]
            [clojure.tools.logging :as log]))

(def parse-spec @#'answers-flagger/parse-spec)
(def checkboxize @#'instrument-answers/checkboxize)
(def namespace-map @#'answers-flagger/namespace-map)
(def filter-specs @#'answers-flagger/filter-specs)
(def project-instrument-specs @#'answers-flagger/project-instrument-specs)
(def apply-instrument-specs @#'answers-flagger/apply-instrument-specs)

(defn eval-condition
  [condition namespace]
  (let [resolver (infix/token-resolver namespace)
        parsed   (-> condition
                     (infix/tokenize)
                     (infix/parse-tokens))]
    (infix/rpn parsed resolver)))

(defn eval-answers-condition
  [instrument answers condition]
  (let [namespace-map' (namespace-map (instrument-answers/merge-items-answers instrument answers))]
    (eval-condition condition namespace-map')))

(def test-instrument {:elements  [{:name "2", :item-id 1569, :response-id 1569}
                                  {:name "12", :item-id 1568, :response-id 1568}]
                      :responses {1568 {:response-type "RD",
                                        :options       [{:value          "1"
                                                         :specification? true}
                                                        {:value          "0"
                                                         :specification? false}]},
                                  1569 {:response-type "CB", :options [{:value "e"} {:value "mb"} {:value "sm"} {:value "xx"}]}}})

(def test-answers {:items          {"1568"    "1",
                                    "1569_sm" "0",
                                    "1569_e"  "0",
                                    "1569_mb" "1",
                                    "1569_xx" "0"},
                   :specifications {"1568_1" "spec2", "1569_mb" "spec1"},
                   :sums           {"sum" 50.0, "subscale1" 24, "subscale2" 36}})

(deftest parse-spec-test
  (is (= {:instrument-id 123
          :abbreviation  nil
          :condition     "@8==10"
          :msg           nil})
      (parse-spec " 123 : @8==10:"))
  (is (= {:instrument-id 123
          :abbreviation  nil
          :condition     "@8==10"
          :msg           "hejsan"})
      (parse-spec " 123 : @8==10: hejsan"))
  (is (= {:instrument-id nil
          :abbreviation  "MADRS-S"
          :condition     "@8==10"
          :msg           "hejsan: hoppsan"})
      (parse-spec " MADRS-S : @8==10: hejsan: hoppsan")))

(deftest eval-condition-test
  (is (= 1 (eval-condition "@8==10" {"@8" 10})))
  (is (= 0 (eval-condition "@8==10" {"@8" 11})))
  (is (= 1 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 2})))
  (is (= 0 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 3})))
  (is (= 1 (eval-condition "@8==10||sum==2" {"@8" 11 "sum" 2})))
  (is (= 1 (eval-condition "@8==10||SUM==2" {"@8" 11 "sum" 2})))
  (is (= 1 (eval-condition "@8==10||sum==2" {"@8" 11 "SUM" 2})))
  (is (= 1 (eval-condition "@8a==10" {"@8A" 10})))
  (is (= 1 (eval-condition "@8A==10" {"@8a" 10})))
  (is (= 1 (eval-condition "@8A==-10" {"@8a" "-10"}))))

(deftest checkboxize-test
  (is (= [{:item-id 1569, :checkbox-id "1569_e", :name "2_e", :value "e"}
          {:item-id 1569, :checkbox-id "1569_mb", :name "2_mb", :value "mb"}
          {:item-id 1569, :checkbox-id "1569_sm", :name "2_sm", :value "sm"}
          {:item-id 1569, :checkbox-id "1569_xx", :name "2_xx", :value "xx"}
          {:name "12", :item-id 1568, :response-id 1568}]
         (checkboxize test-instrument))))

(deftest merge-items-answers-test
  (is (= {:items          [{:item-id 1569, :checkbox-id "1569_e", :name "2_e", :value "0", :specification nil}
                           {:item-id 1569, :checkbox-id "1569_mb", :name "2_mb", :value "1", :specification "spec1"}
                           {:item-id 1569, :checkbox-id "1569_sm", :name "2_sm", :value "0", :specification nil}
                           {:item-id 1569, :checkbox-id "1569_xx", :name "2_xx", :value "0", :specification nil}
                           {:name "12", :item-id 1568, :response-id 1568, :value "1", :specification "spec2"}],
          :specifications {"1568_1" "spec2", "1569_mb" "spec1"},
          :sums           {"sum" 50.0, "subscale1" 24, "subscale2" 36}}
         (instrument-answers/merge-items-answers test-instrument test-answers))))

(deftest namespace-map-test
  (is (= {"@2_mb_spec" "spec1",
          "sum"        50.0,
          "@2_e"       "0",
          "subscale2"  36,
          "@2_mb"      "1",
          "@2_sm"      "0",
          "@12_spec"   "spec2",
          "subscale1"  24,
          "@2_xx"      "0",
          "@12"        "1"}
         (namespace-map (instrument-answers/merge-items-answers test-instrument test-answers)))))


(deftest eval-answers-condition-test
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==0")))
  (is (= 0 (eval-answers-condition test-instrument test-answers "@2_e==1")))
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==0 && subscale1==24")))
  (is (= 0 (eval-answers-condition test-instrument test-answers "@2_e==1 && subscale1==24")))
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==1 || subscale1==24"))))


(deftest eval-spec-test
  (let [namespace (namespace-map (instrument-answers/merge-items-answers test-instrument test-answers))]
    (is (false? (:match? (answers-flagger/eval-spec {:condition "@12==10"}
                                                    namespace))))
    (is (true? (:match? (answers-flagger/eval-spec {:condition "@12==1"}
                                                   namespace))))
    (is (nil? (:error (answers-flagger/eval-spec {:condition "@12==1"}
                                                 namespace))))
    (is (string? (:error (answers-flagger/eval-spec {:condition "@2==1"}
                                                    namespace))))))

;; ---------------------------------
;;    FILTER SPECS ON INSTRUMENT
;; ---------------------------------

(def test-filter-conditions {101   [{:instrument-id nil, :abbreviation "MADRS-S"}
                                    {:instrument-id 1, :abbreviation nil}],
                             102   [{:instrument-id nil, :abbreviation "PHQ-9"}
                                    {:instrument-id nil, :abbreviation "MADRS-S"}
                                    {:instrument-id 2, :abbreviation nil}],
                             :test [{:instrument-id nil, :abbreviation "GAD-7"}
                                    {:instrument-id 3, :abbreviation nil}]})

(deftest filter-specs-test
  (is (= {101 [{:instrument-id nil, :abbreviation "MADRS-S"}
               {:instrument-id 1, :abbreviation nil}]
          102 [{:instrument-id nil, :abbreviation "MADRS-S"}]}
         (filter-specs {:instrument-id 1 :abbreviation "MADRS-S"} test-filter-conditions)))
  (is (= {102 [{:instrument-id 2, :abbreviation nil}]}
         (filter-specs {:instrument-id 2} test-filter-conditions)))
  (is (= {:test [{:instrument-id nil, :abbreviation "GAD-7"}]}
         (filter-specs {:abbreviation "GAD-7"} test-filter-conditions))))


;; ---------------------------------------------
;;    FILTER SPECS ON INSTRUMENT AND PROJECT
;; ---------------------------------------------

(def project-instrument-specs-conditions {101     [{:instrument-id 3, :abbreviation nil :condition "sum==1"}],
                                          102     [{:instrument-id nil, :abbreviation "PHQ-9" :condition "sum==3"}
                                                   {:instrument-id nil, :abbreviation "MADRS-S" :condition "sum==4"}
                                                   {:instrument-id nil, :abbreviation "MADRS-S" :condition "sum==5"}],
                                          :test   [{:instrument-id 3, :abbreviation nil :condition "sum==7"}]
                                          :global [{:instrument-id nil, :abbreviation "GAD-7" :condition "sum==8"}
                                                   {:instrument-id 3, :abbreviation nil :condition "sum==7"}]})

(deftest project-instrument-specs-test
  (is (= #{"sum==4" "sum==5" "sum==7"}
         (->> (project-instrument-specs
                project-instrument-specs-conditions
                102
                {:instrument-id 3 :abbreviation "MADRS-S"})
              (map :condition)
              (into #{})))))

(deftest answer-flags-test
  (is (= ["sum==1"]
         (->> (apply-instrument-specs [{:condition "sum==1"}
                                       {:condition "sum==2"}
                                       ;; This one causes error which should be caught
                                       {:condition "@2==2"}]
                                      {"sum" 1
                                       "@1"  10})
              (map :condition)))))

;; -------------------------------------------------
;;   INSTRUMENT SCORING (MOVE TO SEPARATE TEST NS)
;; -------------------------------------------------

(def expression-resolver @#'instruments/expression-resolver)

(deftest test-scoring-expressions
  (let [evaluator (expression-resolver 20)]
    (is (= 20 (evaluator "$8+10" {"$8" "10"})))
    (is (= 0 (evaluator "$8+10" {"$8" "-10"})))
    ;; Missing $items scored as 20
    (is (= 30 (evaluator "$8+10" {})))
    ;; Missing sums scored as 0
    (is (= 10 (evaluator "x+10" {})))))