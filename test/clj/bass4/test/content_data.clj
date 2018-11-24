(ns bass4.test.content_data
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.content-data :as content-data]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.test :refer :all]))

(use-fixtures
  :once
  test-fixtures)

(deftest no-post
  (is (nil? (content-data/save-content-data! [] 666)))
  (is (thrown? Exception (content-data/save-content-data! {"hejsan" 88} 666)))
  (is (thrown? Exception (content-data/save-content-data! {"hejsan.hoppsan" 88} 666)))
  (is (= nil (content-data/save-content-data! {"hejsan$hoppsan" "88"} 666))))
