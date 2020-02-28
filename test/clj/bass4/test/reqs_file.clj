(ns ^:eftest/synchronized
  bass4.test.reqs-file
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [clojure.string :as str]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest long-filename
  (-> *s*
      (visit "/File.php?uploadedfile=xx")
      (has (status? 404))
      (visit (str "/File.php?uploadedfile=" (str/join (repeat 1e4 "x"))))
      (has (status? 404))))

(deftest content-type-file-404
  (let [res     (visit *s* "/File.php?uploadedfile=xx")     ; Check if it includes proper header
        headers (get-in res [:response :headers])]
    (has res (status? 404))
    (is (true? (contains? headers "Content-Type")))))

(deftest content-type-db-404
  (let [res     (visit *s* "/login" :headers {"x-forwarded-host" "xxxx"})
        headers (get-in res [:response :headers])]
    (has res (status? 404))
    (is (true? (contains? headers "Content-Type")))))