(ns bass4.test.answers-flagger
  (:require [clojure.test :refer :all]
            [bass4.instrument.flagger :as answers-flagger]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.instrument.preview :as instrument-preview]))

(def parse-spec @#'answers-flagger/parse-spec)
(def eval-condition @#'answers-flagger/eval-condition)
(def checkboxize @#'instrument-answers/checkboxize)
(def namespace-map @#'answers-flagger/namespace-map)
(def eval-answers-condition @#'answers-flagger/eval-answers-condition)
(def filter-specs @#'answers-flagger/filter-specs)

(def test-instrument {:elements  [{:name "2", :item-id 1569, :response-id 1569}
                                  {:name "12", :item-id 1568, :response-id 1568}]
                      :responses {1568 {:response-type "RD",
                                        :options       [{:value          "1",
                                                         :specification? true,}
                                                        {:value          "0",
                                                         :specification? false,}],},
                                  1569 {:response-type "CB", :options [{:value "e"} {:value "mb"} {:value "sm"} {:value "xx"}]}}})

(def test-answers {:items          {"1568"    "1",
                                    "1569_sm" "0",
                                    "1569_e"  "0",
                                    "1569_mb" "1",
                                    "1569_xx" "0"},
                   :specifications {"1568_1" "spec2", "1569_mb" "spec1"},
                   :sums           {"sum" 50.0, "subscale1" 24, "subscale2" 36}})

(def test-filter-conditions {101   [{:instrument-id nil, :abbreviation "MADRS-S"}
                                    {:instrument-id 1, :abbreviation nil}],
                             102   [{:instrument-id nil, :abbreviation "PHQ-9"}
                                    {:instrument-id nil, :abbreviation "MADRS-S"}
                                    {:instrument-id 2, :abbreviation nil}],
                             :test [{:instrument-id nil, :abbreviation "GAD-7"}
                                    {:instrument-id 3, :abbreviation nil}]})

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
  (is (= 1 (eval-condition "@8==10||sum==2" {"@8" 11 "sum" 2}))))

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
  (is (= {"1569_mb_spec" "spec1",
          "sum"          50.0,
          "@2_e"         "0",
          "subscale2"    36,
          "@2_mb"        "1",
          "@2_sm"        "0",
          "1568_1_spec"  "spec2",
          "subscale1"    24,
          "@2_xx"        "0",
          "@12"          "1"}
         (namespace-map test-instrument test-answers))))


(deftest eval-answers-condition-test
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==0")))
  (is (= 0 (eval-answers-condition test-instrument test-answers "@2_e==1")))
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==0 && subscale1==24")))
  (is (= 0 (eval-answers-condition test-instrument test-answers "@2_e==1 && subscale1==24")))
  (is (= 1 (eval-answers-condition test-instrument test-answers "@2_e==1 || subscale1==24"))))

(deftest filter-specs-test
  (is (= {101 [{:instrument-id nil, :abbreviation "MADRS-S"}
               {:instrument-id 1, :abbreviation nil}]
          102 [{:instrument-id nil, :abbreviation "MADRS-S"}]}
         (filter-specs {:instrument-id 1 :abbreviation "MADRS-S"} test-filter-conditions)))
  (is (= {102 [{:instrument-id 2, :abbreviation nil}]}
         (filter-specs {:instrument-id 2} test-filter-conditions)))
  (is (= {:test [{:instrument-id nil, :abbreviation "GAD-7"}]}
         (filter-specs {:abbreviation "GAD-7"} test-filter-conditions))))