(ns bass4.test.locals
  (:require [clojure.test :refer :all]
            [bass4.utils :refer :all]
            [bass4.bass-locals :refer :all]))

(deftest t-parse-local
  (is (= {:db-url "jdbc:mysql://localhost:3300/bass4_test?user=root&password=root", :time-zone "Asia/Tokyo", :lang "se"}
         (db-config 3300 (:test (parse-local (clojure.java.io/file "/Users/brjljo/Dropbox/Plattform/dahlia/local_test.php")))))))