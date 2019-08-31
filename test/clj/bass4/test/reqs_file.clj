(ns bass4.test.reqs-file
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
      (log-status)
      (has (status? 404))))