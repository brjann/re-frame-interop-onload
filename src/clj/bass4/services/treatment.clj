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
          (unserialize-key treatment-access :module-accesses #(into #{} (keys (filter-map identity (map-map val-to-bol %))))))
        (db/get-linked-treatments {:user-id user-id})))

(defn- categorize-contents
  [contents module-id]
  (let [categorized (map-map (fn [x] (map :content-id x)) (group-by :type (get contents module-id)))]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     :main-texts (get categorized "MainTexts")}))

(defn- add-contents-to-modules
  [modules worksheets]
  (let [contents-by-module (group-by :module-id worksheets)]
    (map (fn [module]
           (merge module (categorize-contents contents-by-module (:module-id module))))
         modules)))

#_(defn treatment-map
  [treatment-id]
  (let [info     (db/get-treatment-info {:treatment-id treatment-id})
        modules  (db/get-treatment-modules {:treatment-id treatment-id})
        contents (db/get-module-worksheets {:module-ids (map :module-id modules)})]
    (merge info
           {:modules  (add-contents-to-modules modules contents)
            :contents (into {} (map #(identity [(:content-id %) (dissoc % :module-id :type)]) contents))})))

(defn- categorize-module-contents
  [contents]
  (let [categorized (group-by :type contents)]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     :main-texts (get categorized "MainTexts")}))

(defn get-content
  [content-id]
  (db/get-content {:content-id content-id}))

;; TODO: Remove c_module from SQL query
(defn get-module-contents
  [module-id]
  (let [contents (db/get-module-contents {:module-ids [module-id]})]
    (categorize-module-contents contents)))

(defn treatment-map
  [treatment-id]
  (let [info    (db/get-treatment-info {:treatment-id treatment-id})
        modules (db/get-treatment-modules {:treatment-id treatment-id})]
    (merge info
           {:modules modules})))

(defn user-components
  [treatment-access treatment]
  {:modules   (map #(assoc % :active (contains? (:module-accesses treatment-access) (:module-id %))) (:modules treatment))
   :messaging true})

;; TODO: For now, only the first treatment access is considered.
;; If multiple treatments are available, the session needs to keep track
;; of the cTreatmentAccess content in which treatment content is shown.
;; Either in the URL or in a state. Too early to decide now - use case
;; has never surfaced.
(defn user-treatment
  [user-id]
  (if-let [treatment-access (first (user-treatment-accesses user-id))]
    (let [treatment (treatment-map (:treatment-id treatment-access))]
      {:treatment-access treatment-access
       :user-components  (user-components treatment-access treatment)
       :treatment        treatment})))

