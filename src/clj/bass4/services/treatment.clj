(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map]]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Does probably not handle automatic module accesses


;; https://github.com/clojure/clojure/blob/clojure-1.9.0-alpha14/src/clj/clojure/core.clj#L519
(defn boolean?
  "Return true if x is a Boolean"
  {:added "1.9"}
  [x] (instance? Boolean x))


(defn- val-to-bol
  [x]
  (if (boolean? x)
    x
    (not (zero? (str->int x)))))


(defn- user-treatment-accesses
  [user-id]
  (mapv (fn [treatment-access]
          (unserialize-key treatment-access :module-accesses #(keys (filter-map identity (map-map val-to-bol %)))))
        (db/get-linked-treatments {:user-id user-id})))

(defn- categorize-contents
  [contents module-id]
  (let [categorized (map-map (fn [x] (map :content-id x)) (group-by :type (get contents module-id)))]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     :maintexts  (get categorized "MainTexts")}))

(defn- add-contents-to-modules
  [modules worksheets]
  (let [contents-by-module (group-by :module-id worksheets)]
    (map (fn [module]
           (merge module (categorize-contents contents-by-module (:module-id module))))
         modules)))

(defn treatment-map
  [treatment-id]
  (let [info     (db/get-treatment-info {:treatment-id treatment-id})
        modules  (db/get-treatment-modules {:treatment-id treatment-id})
        contents (db/get-module-worksheets {:module-ids (map :module-id modules)})]
    (merge info
           {:modules  (add-contents-to-modules modules contents)
            :contents (into {} (map #(identity [(:content-id %) (dissoc % :module-id :type)]) contents))})))

;; TODO: Merge info from several ongoing treatments
(defn user-treatments
  [user-id]
  (let [treatment-accesses (user-treatment-accesses user-id)
        treatments         (map #(treatment-map (:treatment-id %)) treatment-accesses)]
    {:treatment-accesses   treatment-accesses
     :treatment-components {:messages true
                            :modules  (:module-accesses (first treatment-accesses))}
     :treatments           treatments}))

