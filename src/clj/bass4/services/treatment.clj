(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
(defn user-treatments [user-id]
  (let [treatments (db/get-linked-treatments {:user-id user-id})
        treatments (map #(merge % {:module-accesses (into {} (php->clj (:module-accesses %)))}) treatments)]
    treatments))