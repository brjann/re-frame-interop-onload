(ns bass4.test.answers-flagger
  (:require [clojure.test :refer :all]
            [bass4.instrument.flagger :as answers-flagger]
            [bass4.instrument.preview :as instruments]))

(def parse-spec @#'answers-flagger/parse-spec)
(def eval-condition @#'answers-flagger/eval-condition)
(def checboxize @#'instruments/checkboxize)

(deftest parse-spec-test
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        nil})
      (parse-spec " 123 : @8==10:"))
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        "hejsan"})
      (parse-spec " 123 : @8==10: hejsan"))
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        "hejsan: hoppsan"})
      (parse-spec " 123 : @8==10: hejsan: hoppsan")))

(deftest eval-condition-test
  (is (= 1 (eval-condition "@8==10" {"@8" 10})))
  (is (= 0 (eval-condition "@8==10" {"@8" 11})))
  (is (= 1 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 2})))
  (is (= 0 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 3})))
  (is (= 1 (eval-condition "@8==10||sum==2" {"@8" 11 "sum" 2}))))

(deftest checkboxize-test
  (= [{:item-id 1569, :checkbox-id "1569_e", :name "2_e", :value "e"}
      {:item-id 1569, :checkbox-id "1569_mb", :name "2_mb", :value "mb"}
      {:item-id 1569, :checkbox-id "1569_sm", :name "2_sm", :value "sm"}
      {:item-id 1569, :checkbox-id "1569_xx", :name "2_xx", :value "xx"}
      {:name "12", :item-id 1581, :response-id 1581}]
     (checboxize
       {:elements  [{:name "2", :item-id 1569, :response-id 1569}
                    {:name "12", :item-id 1581, :response-id 1581}]
        :responses {1581 {:response-type "RD"},
                    1569 {:response-type "CB", :options [{:value "e"} {:value "mb"} {:value "sm"} {:value "xx"}]}}})))