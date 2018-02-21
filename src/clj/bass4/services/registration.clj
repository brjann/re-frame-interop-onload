(ns bass4.services.registration
  (:require [bass4.db.core :as db]))

(defn registration-allowed?
  [project-id]
  (:allowed (db/bool-cols db/registration-allowed? {:project-id project-id} [:allowed])))