(ns bass4.test.treatments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.assessments :as assessments]
            [bass4.services.treatment :as treatment]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(deftest no-modules
  (let [treatments       (treatment/user-treatment 538182)
        treatment-access (first (:treatment-accesses treatments))]
    (is (= 3958 (:treatment-id treatment-access)))
    (is (= nil (:module-accesses treatment-access)))))
