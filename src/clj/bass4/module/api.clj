(ns bass4.module.api
  (:require [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.http-utils :refer [url-escape]]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.services.content-data :as content-data-service]
            [bass4.utils :as utils]
            [bass4.module.services :as module-service]
            [bass4.module.builder :as module-builder])
  (:import (org.joda.time DateTime)))


(s/defschema ContentInfo
  {:content-id   s/Int
   :content-name String
   :accessed?    Boolean
   :namespace    String
   :has-text?    s/Bool
   :data-updated (s/maybe DateTime)
   :tags         [String]})

(s/defschema ModuleInfo
  {:module-id       s/Int
   :module-name     String
   :active?         Boolean
   :activation-date (s/maybe DateTime)
   :homework-status (s/maybe (s/enum :ok :submitted))
   :tags            [String]})

(s/defschema ModuleWithContent
  (merge ModuleInfo
         {:main-text  (s/maybe ContentInfo)
          :worksheets [ContentInfo]
          :homework   (s/maybe ContentInfo)}))

(s/defschema MainText
  {:content-id   s/Int
   :content-name String
   :data-imports [String]
   :markdown?    Boolean
   :accessed?    Boolean
   :namespace    String
   :text         (s/maybe String)
   :file-path    (s/maybe String)
   :tags         [String]})

(s/defschema Homework
  (merge
    MainText
    {:status (s/maybe (s/enum :ok :submitted))}))

(s/defschema Worksheet
  MainText)

(defapi modules-list
  [modules :- seq? treatment-access-id :- integer?]
  (when (seq modules)
    (let [modules-with-content (module-builder/assoc-content-info modules treatment-access-id)
          res                  (mapv (fn [module]
                                       (let [content-keys #(select-keys % (keys ContentInfo))
                                             contents     (:contents module)
                                             contents     {:main-text  (utils/fnil+ content-keys (:main-text contents))
                                                           :worksheets (mapv content-keys (:worksheets contents))
                                                           :homework   (utils/fnil+ content-keys (:homework contents))}]
                                         (merge
                                           (select-keys module (keys ModuleWithContent))
                                           contents)))
                                     modules-with-content)]
      (http-response/ok res))))

(defn- get-module
  [module-id modules]
  (let [module (first (filter #(= module-id (:module-id %)) modules))]
    (cond
      (nil? module)
      (http-response/not-found! (str "No such module " module-id))

      (not (:active? module))
      (http-response/forbidden! (str "Module " module-id " not active."))

      :else
      module)))

(defn- module-content
  [treatment-access-id module-id modules get-id-fn schema]
  (let [module (get-module module-id modules)]
    (if-let [content-id (get-id-fn)]
      (let [module-content (module-builder/content-in-module module content-id)]
        (-> module-content
            (select-keys (keys schema))
            (update :data-imports #(into [] %))
            (assoc :accessed? (module-service/content-accessed? treatment-access-id
                                                                module-id
                                                                content-id))))
      (http-response/not-found! (str "Module " module-id " has no such content")))))

(defapi main-text
  [module-id :- api/->int modules :- seq? treatment-access-id]
  (http-response/ok (module-content
                      treatment-access-id
                      module-id
                      modules
                      #(module-service/get-module-main-text-id module-id)
                      MainText)))

(defapi homework
  [module-id :- api/->int modules :- seq? treatment-access-id]
  (let [res    (module-content
                 treatment-access-id
                 module-id
                 modules
                 #(module-service/get-module-homework-id module-id)
                 Homework)
        module (first (filter #(= module-id (:module-id %)) modules))]
    (http-response/ok (assoc res :status (:homework-status module)))))

(defapi homework-submit
  [module-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (let [module (get-module module-id modules)]
    (if (module-service/get-module-homework-id module-id)
      (do
        (module-service/submit-homework! treatment-access-id module)
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " has no homework")))))

(defapi worksheet
  [module-id :- api/->int worksheet-id :- api/->int modules :- seq? treatment-access-id]
  (let [res (module-content
              treatment-access-id
              module-id
              modules
              #(when (module-service/module-has-worksheet? module-id worksheet-id)
                 worksheet-id)
              Worksheet)]
    (http-response/ok res)))

(defapi module-content-access
  [module-id :- api/->int content-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (let [_               (get-module module-id modules)      ;; Error if module not available
        module-contents (module-service/modules-contents [module-id])
        content-ids     (mapv :content-id module-contents)]
    (if (utils/in? content-ids content-id)
      (do
        (module-service/register-content-access! content-id module-id treatment-access-id)
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " does not have content " content-id)))))

(defapi activate-module
  [module-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (if-let [module (first (filter #(= module-id (:module-id %)) modules))]
    (do
      (when-not (:active? module)
        (module-service/activate-module! treatment-access-id module-id))
      (http-response/ok {:result "ok"}))
    (http-response/not-found! (str "No such module " module-id))))

;--------------------
;  CONTENT DATA API
;--------------------

(defapi get-module-content-data
  [module-id :- api/->int content-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (if (module-service/module-has-content? module-id content-id)
    (let [module         (get-module module-id modules)
          module-content (module-builder/content-in-module module content-id)
          content-data   (module-builder/module-content-data
                           treatment-access-id
                           module-content)]
      (http-response/ok (or content-data {})))
    (http-response/not-found (str "Module " module-id " does not have content " content-id))))

(defn- data-map->vec
  [data]
  (try
    (reduce-kv
      (fn [init namespace key-values]
        (into init
              (map (fn [[key value]]
                     [(name namespace) (name key) value]) key-values))) [] data)
    (catch Exception _
      (http-response/bad-request! "'data' parameter in wrong format"))))

(defapi save-module-content-data
  [module-id :- api/->int content-id :- api/->int data :- map? modules :- seq? treatment-access-id :- int?]
  (let [module   (get-module module-id modules)
        data-vec (data-map->vec data)]
    (if (module-service/module-has-content? module-id content-id)
      (do
        (content-data-service/save-api-content-data! data-vec
                                                     treatment-access-id
                                                     (get-in module [:content-ns-aliases content-id]))
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " does not have content " content-id)))))

(defn- size?
  [v]
  (not (zero? (count v))))

(defapi get-content-data
  [namespaces :- [vector? size?] treatment-access-id :- integer?]
  (http-response/ok (content-data-service/get-content-data treatment-access-id namespaces)))

(defapi get-content-data-namespaces
  [treatment-access-id :- integer?]
  (http-response/ok (content-data-service/get-content-data-namespaces treatment-access-id)))

(defapi save-content-data
  [data :- map? treatment-access-id :- int?]
  (let [data-vec (data-map->vec data)]
    (content-data-service/save-api-content-data! data-vec
                                                 treatment-access-id)
    (http-response/ok {:result "ok"})))