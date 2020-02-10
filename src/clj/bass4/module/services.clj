(ns bass4.module.services
  (:require [clojure.set]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.services.bass :as bass]
            [clojure.string :as str]
            [bass4.utils :as utils]
            [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.clients.time :as client-time]
            [clj-time.core :as t]
            [bass4.php-interop :as php-interop]))


;; --------------------------
;;        MODULE INFO
;; --------------------------

(defn content-accessed?
  [treatment-access-id module-id content-id]
  (-> (db/content-first-access {:treatment-access-id treatment-access-id
                                :module-id           module-id
                                :content-id          content-id})
      (seq)
      (boolean)))

(defn get-module-main-text-id
  [module-id]
  (:content-id (db/get-module-main-text-id {:module-id module-id})))

(defn get-module-homework-id
  [module-id]
  (:content-id (db/get-module-homework-id {:module-id module-id})))

(defn module-has-worksheet?
  [module-id worksheet-id]
  (boolean (seq (db/get-module-worksheet-id {:module-id module-id :worksheet-id worksheet-id}))))

(defn module-has-content?
  [module-id content-id]
  (boolean (seq (db/get-module-content-id {:module-id module-id :content-id content-id}))))

(defn submitted-homeworks
  [treatment-access-id]
  (->> (db/get-submitted-homeworks {:treatment-access-id treatment-access-id})
       (group-by :module-id)
       (map-map first)
       #_(map-map #(assoc % :ok (= 1 (:ok %))))))

;; --------------------------
;;     CONTENT RETRIEVAL
;; --------------------------

(defn- split-tags-property
  [container]
  (assoc container :tags (remove empty? (str/split (or (:tags container) "") #" "))))

(defn- check-file
  [content]
  (if (php-interop/uploaded-file (:file-path content))
    content
    (assoc content :file-path nil)))

(defn- check-content-text
  [content]
  (if (empty? (:text content))
    (assoc content :text nil)
    content))

(defn get-content
  "Get content from db."
  [content-id]
  (when-let [content (db/get-content {:content-id content-id})]
    (-> content
        (check-file)
        (check-content-text)
        (unserialize-key :data-imports)
        (split-tags-property)
        ;; Transform true false array for imports into set with imported namespaces
        ((fn [content] (assoc content :data-imports (->> (keys (filter-map identity (:data-imports content)))
                                                         (into #{}))))))))

(defn modules-contents
  [module-ids]
  (->> (db/get-module-contents {:module-ids module-ids})
       (mapv check-file)
       (mapv split-tags-property)
       (filter #(or (:has-text? %) (:file-path %)))))

(defn content-accesses
  [modules treatment-access-id]
  (->> (db/module-content-first-access {:treatment-access-id treatment-access-id :module-ids (mapv :module-id modules)})
       (mapv #(vector (:module-id %) (:content-id %)))
       (into #{})))


;; --------------------------
;;      MODULE RETRIEVAL
;; --------------------------

(defn- unserialize-disabled-imports
  [module]
  (assoc module :content-disabled-imports (->> (php->clj (:content-disabled-imports module))
                                               (into {})
                                               (mapv (fn [[k v]] (let [[content-id namespace] (str/split k #"\$")]
                                                                   {:content-id (utils/str->int content-id)
                                                                    :namespace  namespace
                                                                    :disabled?  v})))
                                               (filter :disabled?)
                                               (group-by :content-id)
                                               (mapv (fn [[k v]] [k (into #{} (mapv :namespace v))]))
                                               (into {}))))

(defn unserialize-more-imports
  [module]
  (let [unser        (->> (php->clj (:content-more-imports module))
                          (into {})
                          (mapv (fn [[k v]] [(str->int k) (str/trim v)]))
                          (remove (fn [[_ v]] (empty? v)))
                          (mapv (fn [[k v]] [k (->> (str/split-lines v)
                                                    (mapv #(let [[namespace alias] (remove empty? (str/split % #" "))]
                                                             [namespace alias]))
                                                    (into {}))]))
                          (into {}))
        more-imports (utils/map-map (comp set keys) unser)
        aliases      (utils/map-map #(utils/filter-map identity %) unser)]
    (-> module
        (assoc :content-ns-imports more-imports :content-ns-aliases aliases)
        (dissoc :content-more-imports))))

(defn- process-modules
  [modules]
  (->> modules
       (mapv split-tags-property)
       (mapv #(unserialize-key % :content-namespaces))
       (mapv unserialize-disabled-imports)
       (mapv unserialize-more-imports)
       (mapv (fn [m] (assoc m :content-namespaces
                              (filter-map #(not (empty? %)) (:content-namespaces m)))))))

(defn get-module
  [module-id]
  (-> (db/get-module {:module-id module-id})
      (vector)
      (process-modules)
      (first)))

(defn get-treatment-modules
  [treatment-id]
  (-> (db/get-treatment-modules {:treatment-id treatment-id})
      (process-modules)))


;; --------------------------
;;    MODULE MUTATIONS
;; --------------------------

(defn activate-module!
  [treatment-access-id module-id]
  (let [module-accesses-string (-> (db/get-module-accesses {:treatment-access-id treatment-access-id})
                                   (:module-accesses)
                                   (php->clj)
                                   (assoc module-id (utils/to-unix (t/now)))
                                   (clj->php))]
    (db/update-module-accesses! {:treatment-access-id treatment-access-id
                                 :module-accesses     module-accesses-string})))

(defn submit-homework!
  ([treatment-access-id module]
   (submit-homework! treatment-access-id module (t/now)))
  ([treatment-access-id module now]
   (db/submit-homework! {:treatment-access-id treatment-access-id
                         :module-id           (:module-id module)
                         :now                 now})))

(defn retract-homework!
  [treatment-access module]

  (db/retract-homework! {:treatment-access-id (:treatment-access-id treatment-access)
                         :module-id           (:module-id module)}))


(defn register-content-access!
  [content-id module-id treatment-access-id]
  (db/register-content-access!
    {:content-id          content-id
     :module-id           module-id
     :treatment-access-id treatment-access-id}))