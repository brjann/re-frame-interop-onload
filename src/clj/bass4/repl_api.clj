(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [clj-time.core :as t]))

(defn hash-password
  [password]
  (user-service/password-hasher password))

