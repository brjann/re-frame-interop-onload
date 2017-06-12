(ns bass4.test.locals
  (:require [clojure.test :refer :all]
            [bass4.utils :refer :all]
            [bass4.bass-locals :refer :all]
            [bass4.test.core :refer [get-edn]]))

(deftest t-parse-local
  (is (= (get-edn "parse-local")
         (db-config 3300 (:test (parse-local (clojure.java.io/file "/Users/brjljo/Dropbox/Plattform/dahlia/local_test.php")))))))