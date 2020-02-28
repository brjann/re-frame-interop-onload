(ns ^:eftest/synchronized
  bass4.test.content_data
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.content-data :as content-data]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.module.responses :as modules-response]))

(use-fixtures
  :once
  test-fixtures)

(deftest no-post
  (is (true? (modules-response/handle-content-data [] 666)))
  (is (thrown? Exception (modules-response/handle-content-data {"hejsan" 88} 666)))
  (is (thrown? Exception (modules-response/handle-content-data {"hejsan" 88} 666)))
  (is (thrown? Exception (modules-response/handle-content-data {"hejsan.hoppsan" 88} 666)))
  (is (true? (modules-response/handle-content-data {"hejsan$hoppsan" "88"} 666))))
