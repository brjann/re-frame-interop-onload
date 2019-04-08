(ns bass4.module.builder
  (:require [bass4.php-clj.safe :refer [php->clj]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.module.services :as module-service]
            [bass4.services.content-data :as content-data]
            [clojure.set :as set]))

;; --------------------
;;  MODULE CONTENT DATA
;; --------------------

(defn get-module-content-data
  [treatment-access-id module-content]
  (let [data-imports (:data-imports module-content)
        namespaces   (conj data-imports (:namespace module-content))
        aliasing     (:ns-aliases module-content)
        data         (content-data/get-content-data
                       treatment-access-id
                       namespaces)]
    (set/rename-keys data aliasing)))

;; --------------------
;;   GET FULL CONTENT
;; --------------------




(defn- inject-module-namespace
  "Inject module-specific namespace into contents"
  [content module]
  (if-let [namespace (get-in module [:content-namespaces (:content-id content)])]
    (assoc content :namespace namespace)
    content))

(defn- inject-module-imports
  [content module]
  (let [content-id   (:content-id content)
        data-imports (:data-imports content)
        disabled     (get-in module [:content-disabled-imports content-id])
        more         (get-in module [:content-ns-imports content-id])
        aliases      (get-in module [:content-ns-aliases content-id])]
    (-> content
        (assoc :data-imports (-> data-imports
                                 (set/difference disabled)
                                 (set/union more)))
        (assoc :ns-aliases aliases))))

(defn get-content-in-module
  [module content-id]
  ;; TODO: This function cannot rely on content existing
  ;; path: api-get-module-content-data
  (some-> (module-service/get-content content-id)
          (inject-module-namespace module)
          (inject-module-imports module)))

;; --------------------------
;;  MODULE CONTENTS SUMMARY
;; --------------------------


(defn- get-module-contents
  [modules]
  (let [modules       (if (sequential? modules)
                        modules
                        [modules])
        modules-by-id (map-map first (group-by :module-id modules))
        contents      (module-service/module-contents (keys modules-by-id))]
    (mapv #(inject-module-namespace % (get modules-by-id (:module-id %))) contents)))

;; --------------------------
;;   CONTENT CATEGORIZATION
;; --------------------------

(defn- categorize-module-contents
  [contents]
  (let [categorized (group-by :type contents)]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     ;; TODO: Handle multiple main texts
     :main-text  (first (get categorized "MainTexts"))}))

(defn get-categorized-module-contents
  [module]
  (-> (get-module-contents module)
      (categorize-module-contents)))

(defn get-module-contents-with-update-time
  [modules treatment-access-id]
  (let [last-updates     (content-data/content-last-updates treatment-access-id)
        content-accesses (module-service/content-accesses modules treatment-access-id)
        contents         (->> (get-module-contents modules)
                              (mapv #(assoc % :data-updated (get-in last-updates [(:namespace %) :time])))
                              (mapv #(assoc % :accessed? (contains? content-accesses [(:module-id %) (:content-id %)]))))]
    (map-map categorize-module-contents (group-by :module-id contents))))

(defn get-modules-with-content
  [modules treatment-access-id]
  (let [module-contents (get-module-contents-with-update-time
                          modules
                          treatment-access-id)]
    (mapv #(assoc % :contents (get module-contents (:module-id %))) modules)))
