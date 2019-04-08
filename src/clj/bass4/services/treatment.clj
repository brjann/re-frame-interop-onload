(ns bass4.services.treatment
  (:require [clojure.set]
            [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.time :as b-time]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.services.module :as module-service]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Does probably not handle automatic module accesses



(defn user-treatment-accesses
  [user-id]
  ;; Evidently, the access dates can be strings when 0 and boolean when automatic access
  ;; Bool true will lead to weird access dates (unix time 1) - which would have to be handled later.
  (let [active-modules   #(into #{} (keys (filter-map identity (map-map val-to-bool %))))
        access-date->int (comp str->int #(if (boolean? %) (if % 1 0) %))]
    (mapv (fn [treatment-access]
            (-> treatment-access
                (unserialize-key :module-accesses #(map-map access-date->int %))
                (#(assoc % :modules-active (active-modules (:module-accesses %))))
                (#(assoc % :modules-activation-dates (map-map b-time/from-unix (filter-map (complement zero?) (:module-accesses %)))))
                (#(assoc % :submitted-homeworks (module-service/submitted-homeworks (:treatment-access-id %))))
                (dissoc :module-accesses)))
          (db/get-treatment-accesses
            {:user-id user-id}))))


(defn treatment-info
  [treatment-id]
  (-> (db/get-treatment-info
        {:treatment-id treatment-id})
      (unserialize-key
        :modules-automatic-access
        #(into #{} (keys (filter-map identity (map-map val-to-bool %)))))))



