(ns bass4.services.bass
  (:require [bass4.db.core :as db]))

(defn project-title []
  (:title (db/get-project-title)))