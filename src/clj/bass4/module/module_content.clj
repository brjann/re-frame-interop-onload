(ns bass4.module.module-content
  (:require [clojure.set :as set]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.utils :refer [unserialize-key map-map str->int filter-map val-to-bool fnil+]]
            [bass4.module.services :as module-service]
            [bass4.services.content-data :as content-data-service]))

;; -----------------------
;;      INTERNAL FNS
;; -----------------------

(defn- inject-module-namespace
  "Inject module-specific namespace into content"
  [content module]
  (if-let [namespace (get-in module [:content-namespaces (:content-id content)])]
    (assoc content :namespace namespace)
    content))

(defn- inject-module-imports
  "Inject module-specific namespace imports into content"
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

(defn- categorize-module-contents
  "Categorizes list of module contents"
  [contents]
  (let [categorized (group-by :type contents)]
    {:worksheets (get categorized "Worksheets")
     :homework   (first (get categorized "Homework"))
     ;; TODO: Handle multiple main texts
     :main-text  (first (get categorized "MainTexts"))}))

(defn- modules-contents
  "Returns unsorted and un-categorized list of all contents belonging
  to modules."
  [modules]
  (let [modules-by-id (map-map first (group-by :module-id modules))
        contents      (module-service/modules-contents (keys modules-by-id))]
    (mapv (fn [content] (let [module (get modules-by-id (:module-id content))]
                          (-> content
                              (inject-module-namespace module)
                              (inject-module-imports module))))
          contents)))


(comment "To test functions accepting modules for user 'treatment-test'"
         (def modules (:modules (:tx-components (bass4.treatment.builder/user-treatment 583461))))
         (def treatment-access-id (:treatment-access-id (:treatment-access (bass4.treatment.builder/user-treatment 583461)))))

;; --------------------------
;;        PUBLIC FNS
;; --------------------------

(defn module-content-data
  "Content data within module with aliases applied"
  [treatment-access-id module-content]
  (let [data-imports (:data-imports module-content)
        namespaces   (conj data-imports (:namespace module-content))
        aliasing     (:ns-aliases module-content)
        data         (content-data-service/get-content-data
                       treatment-access-id
                       namespaces)]
    (set/rename-keys data aliasing)))

(defn assoc-accessed?
  "Add accessed? key to module content"
  [module-content module-id treatment-access-id]
  (assoc module-content :accessed? (module-service/content-accessed?
                                     treatment-access-id
                                     module-id
                                     (:content-id module-content))))

(defn content-in-module
  "Specific content within a module with module namespaces injected.
  Caller guarantees that content exists and belongs to module"
  [module content-id]
  (if-let [content (module-service/get-content content-id)]
    (-> content
        (inject-module-namespace module)
        (inject-module-imports module))
    (throw (Exception. (str "Content " content-id " does not exist")))))

(defn contents-by-category
  "Categorized module contents for a specific module.
  Used by modules HTML response"
  [module]
  (-> (modules-contents [module])
      (categorize-module-contents)))

(defn assoc-content-info
  "Adds content info including last data changes
  to a list of modules.
  Used by HTML response and API module lists"
  [modules treatment-access-id]
  (let [last-updates     (content-data-service/namespaces-last-updates treatment-access-id)
        content-accesses (module-service/content-accesses modules treatment-access-id)
        contents         (->> (modules-contents modules)
                              (mapv #(assoc % :data-updated (get-in last-updates [(:namespace %) :time])))
                              (mapv #(assoc % :accessed? (contains? content-accesses [(:module-id %) (:content-id %)]))))
        categorized      (map-map categorize-module-contents (group-by :module-id contents))]
    (mapv #(assoc % :contents (get categorized (:module-id %)))
          modules)))