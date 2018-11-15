(ns bass4.test.answers-validation
  (:require [clojure.test :refer :all]
            [bass4.instruments.validation :refer :all]
            [bass4.utils :as utils]
            [clojure.tools.logging :as log]))



(defn get-purified-items
  [instrument]
  (let [items (->> (get-items-map instrument)
                   (utils/map-map #(dissoc %
                                           :page-break
                                           :option-separator
                                           :text
                                           :layout
                                           :item-id
                                           :layout-id
                                           :check-error-text
                                           :response-id
                                           :option-jumps
                                           :vas-min-label
                                           :vas-max-label))
                   (utils/map-map (fn [item]
                                    (assoc item
                                      :options
                                      (when (contains? #{"RD" "CB"} (:response-type item))
                                        (utils/map-map
                                          #(select-keys % [:specification? :jump])
                                          (:options item))))))
                   (utils/map-map #(utils/filter-map identity %)))]
    items))


(deftest merge-answers-x
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {1 {:response-type "CB", :name "1", :options {"X" {:jump [2]}, "Y" {}}, :answer {"X" "1"}},
            2 {:response-type "CB", :name "2", :options {"Z" {}, "W" {}}, :answer {"Z" "1", "W" "0"}},
            3 {:response-type "TX", :name "3", :answer {3 "x"}}}
           (merge-answers items {"1_X" "1", "1_Y" "", "2_Z" "1", "2_W" "0", "3" "x"})))))




;; ------------------------
;;         JUMPS
;; ------------------------

(deftest jumps
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"1" {:jump [2]} "0" {}}}
               2 {:response-type "TX"
                  :name          "2"}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:jumps #{2}} (validate-answers* items {"1" "1", "2" "x", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "0", "2" "x", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "1", "2" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "1", "3" "x"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"1" {:jump [2 3]} "0" {}}}
               2 {:response-type "TX"
                  :name          "2"}
               3 {:response-type "TX"
                  :name          "3"}
               4 {:response-type "TX"
                  :name          "4"}}]
    (is (= {:jumps #{2 3}} (validate-answers* items {"1" "1", "2" "x", "3" "x", "4" "x"} {})))
    (is (nil? (validate-answers* items {"1" "1", "2" "", "3" "", "4" "x"} {})))
    (is (nil? (validate-answers* items {"1" "1", "4" "x"} {}))))
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:jumps #{2}} (validate-answers* items {"1_X" "1", "1_Y" "0", "2_Z" "1", "2_W" "0", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1_X" "1", "1_Y" "1", "2_Z" "1", "2_W" "0", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1_X" "0", "1_Y" "1", "2_Z" "1", "2_W" "0", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1_X" "1", "1_Y" "1", "2_Z" "0", "2_W" "0", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1_X" "1", "1_Y" "1", "2_Z" "", "2_W" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1_X" "1", "1_Y" "1", "3" "x"} {})))))


;; ------------------------
;;         MISSING
;; ------------------------

(deftest missing
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"1" {} "0" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:missing #{1 2 3}} (validate-answers* items {} {})))
    (is (= {:missing #{1 2 3}} (validate-answers* items {"1" "", "2_Z" "", "2_W" "", "3" ""} {})))
    (is (= {:missing #{1 2 3}} (validate-answers* items {"1" "", "2_Z" "0", "2_W" "0", "3" ""} {})))
    (is (= {:missing #{3}} (validate-answers* items {"1" "1", "2_Z" "1", "2_W" "1", "3" ""} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :optional?     true
                  :options       {"1" {} "0" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "CB"
                  :name          "2"
                  :optional?     true
                  :options       {"Z" {} "W" {}}}}]
    (is (= {:missing #{2}} (validate-answers* items {} {})))
    (is (= {:missing #{2}} (validate-answers* items {"1" "", "2_Z" "", "2_W" "0", "3_Z" "0"} {})))
    (is (= {:missing #{2}} (validate-answers* items {"1" "1", "2" "", "3_Z" "1" "3_W" "0"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :optional?     true
                  :options       {"1" {:jump [2]} "0" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "CB"
                  :name          "2"
                  :optional?     true
                  :options       {"Z" {} "W" {}}}}]
    (is (= {:missing #{2}} (validate-answers* items {} {})))
    (is (nil? (validate-answers* items {"1" "1", "3_Z" "1", "3_W" "0"} {})))
    (is (= {:missing #{2}} (validate-answers* items {"1" "0", "2" "", "3_Z" "1", "3_W" "0"} {}))))
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:jumps #{2} :missing #{3}} (validate-answers* items {"1_X" "1", "1_Y" "0", "2_Z" "1", "2_W" "0"} {})))))


;; ------------------------
;;   CHECKBOX CONSTRAINTS
;; ------------------------

(deftest checkbox-contraints
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"Z" {} "W" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}}]
    (is (= {:constraints {1 {:checkboxes-missing #{"W"}}
                          2 {:checkboxes-missing  #{"W"}
                             :checkbox-not-binary #{"Z"}}}}
           (validate-answers* items {"1_Z" "1", "2_Z" "2"} {})))
    (is (= {:constraints
            {1 {:checkboxes-missing #{"Z"}}
             2 {:checkboxes-missing #{"W"}}}}
           (validate-answers* items {"1_W" "1", "2_Z" "1"} {})))
    (is (= {:constraints {1 {:checkbox-not-binary #{"W"}}
                          2 {:checkboxes-missing #{"W"}}}}
           (validate-answers* items {"1_W" "2", "1_Z" "1", "2_Z" "1"} {})))
    (is (= {:constraints {2 {:checkboxes-missing #{"W"}}}
            :missing     #{1}}
           (validate-answers* items {"1_W" "0", "1_Z" "", "2_Z" "1"} {}))))
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"Z" {:jump [2]} "W" {}}}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}
               3 {:response-type "RD"
                  :name          "3"
                  :options       {"Z" {} "W" {}}}}]
    (is (= {:constraints {1 {:checkboxes-missing #{"W"}}}
            :jumps       #{2}}
           (validate-answers* items {"1_Z" "1", "1_W" "", "2_Z" "1", "2_W" "1", "3" "Z"} {}))))
  (let [items {1 {:response-type "CB"
                  :name          "1"
                  :options       {"Z" {} "W" {}}
                  :optional?     true}
               2 {:response-type "CB"
                  :name          "2"
                  :options       {"Z" {} "W" {}}}}]
    (is (= {:constraints {1 {:checkboxes-missing #{"W"}}
                          2 {:checkboxes-missing  #{"W"}
                             :checkbox-not-binary #{"Z"}}}}
           (validate-answers* items {"1_Z" "1", "2_Z" "2"} {})))
    (is (= {:constraints
            {1 {:checkboxes-missing #{"Z"}}
             2 {:checkboxes-missing #{"W"}}}}
           (validate-answers* items {"1_W" "1", "2_Z" "1"} {})))
    (is (= {:constraints {1 {:checkbox-not-binary #{"W"}}
                          2 {:checkboxes-missing #{"W"}}}}
           (validate-answers* items {"1_W" "2", "1_Z" "1", "2_Z" "1"} {})))
    (is (= {:constraints {2 {:checkboxes-missing #{"W"}}}}
           (validate-answers* items {"1_W" "0", "1_Z" "", "2_Z" "1"} {})))))


;; ------------------------
;;  RADIOBUTTON CONSTRAINTS
;; ------------------------

(deftest radiobutton-contraints
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "RD"
                  :name          "2"
                  :options       {"1" {} "2" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:constraints {1 {:radio-invalid-value "1"}
                          2 {:radio-invalid-value "x"}}}
           (validate-answers* items {"1" "1", "2" "x", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "1", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "X", "3" "x"} {})))
    (is (= {:missing #{2}} (validate-answers* items {"1" "Y", "2" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "1", "3" "x"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "RD"
                  :name          "2"
                  :optional?     true
                  :options       {"1" {} "2" {}}}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:constraints {1 {:radio-invalid-value "1"}
                          2 {:radio-invalid-value "x"}}}
           (validate-answers* items {"1" "1", "2" "x", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "1", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "X", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "1", "3" "x"} {})))))

;; ------------------------
;;     VAS CONSTRAINTS
;; ------------------------

(deftest vas-contraints
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "VS"
                  :name          "2"}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:constraints {2 {:vas-invalid "x"}}} (validate-answers* items {"1" "Y", "2" "x", "3" "x"} {})))
    (is (= {:constraints {2 {:vas-invalid "401"}}} (validate-answers* items {"1" "Y", "2" "401", "3" "x"} {})))
    (is (= {:constraints {2 {:vas-invalid "-1"}}} (validate-answers* items {"1" "Y", "2" "-1", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "X", "3" "x"} {})))
    (is (= {:missing #{2}} (validate-answers* items {"1" "Y", "2" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "1", "3" "x"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"X" {:jump [2]} "Y" {}}}
               2 {:response-type "VS"
                  :name          "2"
                  :optional?     true}
               3 {:response-type "TX"
                  :name          "3"}}]
    (is (= {:constraints {2 {:vas-invalid "x"}}} (validate-answers* items {"1" "Y", "2" "x", "3" "x"} {})))
    (is (= {:constraints {2 {:vas-invalid "401"}}} (validate-answers* items {"1" "Y", "2" "401", "3" "x"} {})))
    (is (= {:constraints {2 {:vas-invalid "-1"}}} (validate-answers* items {"1" "Y", "2" "-1", "3" "x"} {})))
    (is (= {:jumps #{2}} (validate-answers* items {"1" "X", "2" "X", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "", "3" "x"} {})))
    (is (nil? (validate-answers* items {"1" "Y", "2" "1", "3" "x"} {})))))


;; ------------------------
;;  TEXT CONSTRAINTS RANGE
;; ------------------------
(deftest text-contraints-range
  (let [items {1 {:response-type "TX"
                  :name          "1"
                  :range-max     10
                  :range-min     -2}}]
    (is (= {:constraints {1 {:range-error "x"}}} (validate-answers* items {"1" "x"} {})))
    (is (= {:constraints {1 {:range-error "-3"}}} (validate-answers* items {"1" "-3"} {})))
    (is (= {:constraints {1 {:range-error "11"}}} (validate-answers* items {"1" "11"} {})))
    (is (= {:missing #{1}} (validate-answers* items {"1" ""} {})))
    (is (nil? (validate-answers* items {"1" "-2"} {})))
    (is (nil? (validate-answers* items {"1" "10"} {}))))
  (let [items {1 {:response-type "TX"
                  :name          "1"
                  :range-max     10}}]
    (is (= {:constraints {1 {:range-error "x"}}} (validate-answers* items {"1" "x"} {})))
    (is (= {:constraints {1 {:range-error "11"}}} (validate-answers* items {"1" "11"} {})))
    (is (= {:missing #{1}} (validate-answers* items {"1" ""} {})))
    (is (nil? (validate-answers* items {"1" "-2"} {})))
    (is (nil? (validate-answers* items {"1" "10"} {}))))
  (let [items {1 {:response-type "TX"
                  :name          "1"
                  :range-min     -10}}]
    (is (= {:constraints {1 {:range-error "x"}}} (validate-answers* items {"1" "x"} {})))
    (is (= {:constraints {1 {:range-error "-11"}}} (validate-answers* items {"1" "-11"} {})))
    (is (= {:missing #{1}} (validate-answers* items {"1" ""} {})))
    (is (nil? (validate-answers* items {"1" "-2"} {})))
    (is (nil? (validate-answers* items {"1" "10"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"1" {:jump [2]} "0" {}}}
               2 {:response-type "TX"
                  :name          "2"
                  :range-max     10
                  :range-min     -2
                  :optional?     true}}]
    (is (= {:jumps #{2}} (validate-answers* items {"1" "1" "2" "-3"} {})))
    (is (= {:constraints {2 {:range-error "-3"}}}
           (validate-answers* items {"1" "0" "2" "-3"} {})))
    (is (nil? (validate-answers* items {"1" "0" "2" ""} {})))))

;; ------------------------
;;  TEXT CONSTRAINTS REGEX
;; ------------------------

(deftest text-contraints-regex
  (let [items {1 {:response-type "TX"
                  :name          "1"
                  :regex         "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))$"}}]
    (is (= {:constraints {1 {:regex-error "x"}}} (validate-answers* items {"1" "x"} {})))
    (is (= {:missing #{1}} (validate-answers* items {"1" ""} {})))
    (is (nil? (validate-answers* items {"1" "1985-01-01"} {}))))
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"1" {:jump [2]} "0" {}}}
               2 {:response-type "TX"
                  :name          "2"
                  :regex         "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))$"
                  :optional?     true}}]
    (is (= {:jumps #{2}} (validate-answers* items {"1" "1" "2" "-3"} {})))
    (is (= {:constraints {2 {:regex-error "-3"}}}
           (validate-answers* items {"1" "0" "2" "-3"} {})))
    (is (nil? (validate-answers* items {"1" "0" "2" ""} {})))))

;; ------------------------
;;     SPECIFICATIONS
;; ------------------------

(deftest specifications
  (let [items {1 {:response-type "RD"
                  :name          "1"
                  :options       {"X" {:jump [2] :specification? true} "Y" {}}}
               2 {:response-type "RD"
                  :name          "2"
                  :optional?     true
                  :options       {"1" {} "2" {:specification? true}}}
               3 {:response-type "CB"
                  :name          "3"
                  :options       {"Z" {:specification? true} "W" {}}}}]
    (is (= {:missing-specs #{"3_Z"
                             "2_2"}}
           (validate-answers* items {"1" "Y", "2" "2", "3_Z" "1", "3_W" "0"} {})))
    (is (= {:missing-specs #{"1_X"
                             "3_Z"}}
           (validate-answers* items {"1" "X", "2" "", "3_Z" "1", "3_W" "0"} {})))
    (is (= {:jumps         #{2}
            :missing-specs #{"1_X"
                             "3_Z"}}
           (validate-answers* items {"1" "X", "2" "2", "3_Z" "1", "3_W" "0"} {})))
    (is (= {:jumps         #{2}
            :missing-specs #{"1_X"
                             "3_Z"}}
           (validate-answers*
             items
             {"1" "X", "2" "2", "3_Z" "1", "3_W" "0"}
             {"1_X" ""})))
    (is (= {:missing-specs #{"2_2"
                             "3_Z"}}
           (validate-answers*
             items
             {"1" "Y", "2" "2", "3_Z" "1", "3_W" "0"}
             {"1_X" "X"})))
    (is (nil? (validate-answers*
                items
                {"1" "Y", "2" "2", "3_Z" "1", "3_W" "0"}
                {"1_X" "X", "2_2" "X", "3_Z" "X"})))))

;; ------------------------
;;    DEMO QUESTIONNAIRE
;; ------------------------

(deftest demo-questionnaire
  (let [items {1581 {:response-type "RD",
                     :name          "12",
                     :options       {"0" {:specification? false, :jump nil}, "1" {:specification? false, :jump nil}}},
               1579 {:response-type "TX", :name "11", :regex "^[0-9]$"},
               1582 {:response-type "ST", :name "12b", :optional? true, :regex "^[0-9]$"},
               1573 {:response-type "ST", :name "6", :range-max 9, :regex "", :range-min 0},
               1724 {:response-type "TX", :name "14", :regex ""},
               1571 {:response-type "VS", :name "4"},
               1575 {:response-type "TX", :name "8", :optional? true, :regex ""},
               1572 {:response-type "RD",
                     :name          "5",
                     :options       {"1" {:specification? false, :jump nil}, "0" {:specification? false, :jump [1573]}}},
               1577 {:response-type "RD",
                     :name          "10",
                     :options       {"1" {:specification? false, :jump nil}, "0" {:specification? false, :jump [1578 1579 1580]}}},
               1569 {:response-type "CB",
                     :name          "2",
                     :options       {"e"  {:specification? false, :jump nil},
                                     "mb" {:specification? true, :jump nil},
                                     "sm" {:specification? false, :jump nil},
                                     "xx" {:specification? true, :jump nil}}},
               1574 {:response-type "TX", :name "7", :optional? true, :regex ""},
               1570 {:response-type "CB",
                     :name          "3",
                     :options       {"rs"    {:specification? false, :jump nil},
                                     "gs"    {:specification? false, :jump [1571 1572 1573 1574 1575 1576 1577 1578]},
                                     "ys"    {:specification? false, :jump nil},
                                     "annan" {:specification? true, :jump nil},
                                     "phd"   {:specification? false, :jump nil},
                                     "fs"    {:specification? false, :jump [1571]},
                                     "u"     {:specification? false, :jump nil},
                                     "fhs"   {:specification? false, :jump [1571 1572 1573 1574 1575 1576 1577 1578 1579 1580 1581 1582]},
                                     "gk"    {:specification? false, :jump nil}}},
               1583 {:response-type "TX", :name "13", :optional? true, :regex ""},
               1576 {:response-type "TX", :name "9", :optional? true, :regex ""},
               1568 {:response-type "RD",
                     :name          "1",
                     :options       {"1" {:specification? true, :jump nil}, "0" {:specification? false, :jump nil}}}}]
    (is (nil? (validate-answers*
                items
                {"1570_gs" "0", "1576" "dsf", "1582" "3", "1570_ys" "0", "1569_sm" "0", "1581" "1", "1570_rs" "0", "1574" "", "1575" "Ja", "1570_u" "0", "1577" "0", "1569_e" "1", "1583" "hejsan", "1572" "0", "1568" "1", "1570_phd" "0", "1570_fs" "1", "1569_mb" "1", "1724" "Hoppsan", "1570_gk" "0", "1570_fhs" "0", "1570_annan" "1", "1569_xx" "0"}
                {"1568_1" "x", "1569_mb" "4", "1570_annan" "df"})))
    (is (nil? (validate-answers*
                items
                {"1570_gs" "1", "1582" "4", "1570_ys" "0", "1569_sm" "0", "1581" "1", "1570_rs" "0", "1570_u" "0", "1569_e" "0", "1583" "dfsfd", "1568" "0", "1570_phd" "0", "1570_fs" "0", "1579" "2", "1569_mb" "1", "1724" "sdfsdf", "1570_gk" "0", "1570_fhs" "0", "1570_annan" "0", "1569_xx" "1"}
                {"1569_mb" "d", "1569_xx" "d"})))
    (is (= {:missing-specs #{"1569_mb"
                             "1569_xx"}}
           (validate-answers*
             items
             {"1570_gs" "1", "1582" "4", "1570_ys" "0", "1569_sm" "0", "1581" "1", "1570_rs" "0", "1570_u" "0", "1569_e" "0", "1583" "dfsfd", "1568" "0", "1570_phd" "0", "1570_fs" "0", "1579" "2", "1569_mb" "1", "1724" "sdfsdf", "1570_gk" "0", "1570_fhs" "0", "1570_annan" "0", "1569_xx" "1"}
             {"1569_mb" ""})))))