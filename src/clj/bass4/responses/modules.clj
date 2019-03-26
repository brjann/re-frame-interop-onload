(ns bass4.responses.modules
  (:require [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [bass4.http-utils :refer [url-escape]]
            [bass4.services.treatment :as treatment-service]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.layout :as layout]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.content-data :as content-data]
            [bass4.i18n :as i18n]
            [bass4.services.content-data :as content-data-service]
            [bass4.http-errors :as http-errors]
            [bass4.utils :as utils]
            [clojure.string :as str])
  (:import (org.joda.time DateTime)))


(defn get-modules-with-content
  [modules treatment-access-id]
  (let [module-contents (treatment-service/get-module-contents-with-update-time
                          modules
                          treatment-access-id)]
    (mapv #(assoc % :contents (get module-contents (:module-id %))) modules)))

(defn- context-menu
  [module module-contents]
  (let [file-php        (fn [content]
                          (str "File.php?uploadedfile="
                               (url-escape (:file-path content))))
        base-path       (str "/user/tx/module/" (:module-id module))
        worksheet-links (fn [worksheet]
                          [(when (:has-text? worksheet)
                             {:link (str base-path "/worksheet/" (:content-id worksheet))
                              :name (:content-name worksheet)})
                           (when (:file-path worksheet)
                             {:link (file-php worksheet)
                              :name (str (i18n/tr [:download]) " " (:content-name worksheet))})])
        main-text       (:main-text module-contents)
        homework        (:homework module-contents)
        items           [(when (:has-text? main-text)
                           {:link (str base-path "/")
                            :name (i18n/tr [:modules/module-text])})
                         (when (:file-path main-text)
                           {:link (file-php main-text)
                            :name (i18n/tr [:modules/download-module-text])})
                         (when (:has-text? homework)
                           {:link (str base-path "/homework")
                            :name (i18n/tr [:modules/homework])})
                         (when (:file-path homework)
                           {:link (file-php homework)
                            :name (i18n/tr [:modules/download-homework])})
                         (flatten (map worksheet-links
                                       (:worksheets module-contents)))]]
    {:items (->> items
                 flatten
                 (remove nil?))
     :title (:module-name module)}))


(defn- module-content-renderer
  [treatment-access render-map module module-contents template content-id & params-map]
  (let [module-content (treatment-service/get-content-in-module module content-id)
        namespace      (:namespace module-content)
        content-data   (treatment-service/get-module-content-data
                         (:treatment-access-id treatment-access)
                         module-content)
        params         (first params-map)]
    (treatment-service/register-content-access!
      content-id
      (:module-id module)
      (:treatment-access-id treatment-access))
    (layout/render
      template
      (merge render-map
             {:text          (:text module-content)
              :file-path     (:file-path module-content)
              :markdown?     (:markdown? module-content)
              :tabbed?       (:tabbed? module-content)
              :show-example? (:show-example? module-content)
              :content-id    content-id
              :namespace     namespace
              :content-data  content-data
              :context-menu  (context-menu module module-contents)
              :page-title    (:content-name module-content)}
             params))))

(defapi main-text
  [treatment-access :- map? render-map :- map? module :- map?]
  (let [module-contents (treatment-service/get-categorized-module-contents module)
        module-text-id  (:content-id (:main-text module-contents))]
    (module-content-renderer
      treatment-access
      render-map
      module
      module-contents
      "module-main-text.html"
      module-text-id
      {:module-id  module-text-id
       :page-title (:module-name module)})))

(defapi homework
  [treatment-access :- map? render-map :- map? module :- map?]
  (let [module-contents (treatment-service/get-categorized-module-contents module)]
    (if-let [homework-id (:content-id (:homework module-contents))]
      (module-content-renderer
        treatment-access
        render-map
        module
        module-contents
        "module-homework.html"
        homework-id
        {:submitted  (get-in treatment-access [:submitted-homeworks (:module-id module)])
         :page-title (str (i18n/tr [:modules/homework]) " " (:module-name module))})
      (http-response/not-found (i18n/tr [:modules/no-homework])))))

(defapi worksheet
  [treatment-access :- map? render-map :- map? module :- map? worksheet-id :- api/->int]
  (let [module-contents (treatment-service/get-categorized-module-contents module)]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (module-content-renderer
        treatment-access
        render-map
        module
        module-contents
        "module-worksheet.html"
        worksheet-id)
      (http-response/not-found (i18n/tr [:modules/no-worksheet])))))

(defapi worksheet-example
  [module :- map? worksheet-id :- api/->int return-path :- [[api/str? 1 2000] api/url?]]
  (let [module-contents (treatment-service/get-categorized-module-contents module)]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (let [content      (treatment-service/get-content worksheet-id)
            namespace    (:namespace content)
            example-data (content-data/get-content-data
                           worksheet-id
                           [namespace])]
        (layout/render "module-worksheet-example.html"
                       {:return-path  return-path
                        :text         (:text content)
                        :markdown?    (:markdown? content)
                        :tabbed?      (:tabbed? content)
                        :content-id   worksheet-id
                        :namespace    namespace
                        :content-data example-data}))
      (http-response/not-found (i18n/tr [:modules/no-worksheet])))))

(defapi modules-list
  [render-map :- map? modules :- seq? treatment-access-id :- integer?]
  (if-not (seq modules)
    (layout/text-response "No modules in treatment")
    (layout/render
      "modules-list.html"
      (merge render-map
             {:modules    (get-modules-with-content modules treatment-access-id)
              :page-title (i18n/tr [:modules/modules])}))))

(defapi view-user-content
  [treatment-access-id :- api/->int module-id :- api/->int content-id :- api/->int]
  (let [module         (treatment-service/get-module module-id)
        module-content (treatment-service/get-content-in-module module content-id)
        namespace      (:namespace module-content)
        content-data   (treatment-service/get-module-content-data treatment-access-id module-content)]
    (layout/render "user-content-viewer.html"
                   {:text         (:text module-content)
                    :markdown?    (:markdown? module-content)
                    :tabbed?      (:tabbed? module-content)
                    :content-id   content-id
                    :namespace    namespace
                    :content-data content-data})))



;--------------
; CONTENT DATA
;--------------

(defn- split-namespace-key-value [[label value]]
  (let [[namespace key] (str/split label #"\$")]
    (when (some empty? [namespace key])
      (http-errors/throw-400! (str "Split pair " label "=" value " failed")))
    [namespace key value]))

(defn handle-content-data
  ([data-map treatment-access-id]
   (handle-content-data data-map treatment-access-id {}))
  ([data-map treatment-access-id ns-aliases]
   (let [data-vec (mapv split-namespace-key-value (into [] data-map))]
     (content-data-service/save-api-content-data!
       data-vec
       treatment-access-id
       ns-aliases))
   true))

(defapi save-worksheet-example-data
  [content-id :- api/->int content-data :- [api/->json map?]]
  (when (handle-content-data content-data content-id)
    (http-response/found "reload")))

(defapi save-worksheet-data
  [treatment-access-id :- integer? module :- map? content-id :- api/->int content-data :- [api/->json map?]]
  (when (handle-content-data content-data
                             treatment-access-id
                             (get-in module [:content-ns-aliases content-id]))
    (http-response/found "reload")))

(defapi save-main-text-data
  [treatment-access-id :- integer? module :- map? content-data :- [api/->json map?]]
  (let [main-text-id (treatment-service/get-module-main-text-id (:module-id module))]
    (when (handle-content-data content-data
                               treatment-access-id
                               (get-in module [:content-ns-aliases main-text-id]))
      (http-response/ok {}))))

(defapi save-homework
  [treatment-access-id :- integer? module :- map? content-data :- [api/->json map?] submit? :- api/->bool]
  (let [homework-id (treatment-service/get-module-homework-id (:module-id module))]
    (when (handle-content-data content-data
                               treatment-access-id
                               (get-in module [:content-ns-aliases homework-id]))
      (when submit?
        (treatment-service/submit-homework! treatment-access-id module))
      (http-response/found "reload"))))

(defapi retract-homework
  [treatment-access :- map? module :- map?]
  (if-let [submitted (get-in treatment-access [:submitted-homeworks (:module-id module)])]
    (do
      (when (not (:ok? submitted))
        (treatment-service/retract-homework! treatment-access module))
      (http-response/found "reload"))
    (http-errors/throw-400!)))


;--------------
;  MODULE API
;--------------

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

(defapi api-modules-list
  [modules :- seq? treatment-access-id :- integer?]
  (when (seq modules)
    (let [modules-with-content (get-modules-with-content modules treatment-access-id)
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
      (let [module-content (treatment-service/get-content-in-module module content-id)]
        (-> module-content
            (select-keys (keys schema))
            (update :data-imports #(into [] %))
            (assoc :accessed? (treatment-service/content-accessed? treatment-access-id
                                                                   module-id
                                                                   content-id))))
      (http-response/not-found! (str "Module " module-id " has no such content")))))

(defapi api-main-text
  [module-id :- api/->int modules :- seq? treatment-access-id]
  (http-response/ok (module-content
                      treatment-access-id
                      module-id
                      modules
                      #(treatment-service/get-module-main-text-id module-id)
                      MainText)))

(defapi api-homework
  [module-id :- api/->int modules :- seq? treatment-access-id]
  (let [res    (module-content
                 treatment-access-id
                 module-id
                 modules
                 #(treatment-service/get-module-homework-id module-id)
                 Homework)
        module (first (filter #(= module-id (:module-id %)) modules))]
    (http-response/ok (assoc res :status (:homework-status module)))))

(defapi api-homework-submit
  [module-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (let [module (get-module module-id modules)]
    (if (treatment-service/get-module-homework-id module-id)
      (do
        (treatment-service/submit-homework! treatment-access-id module)
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " has no homework")))))

(defapi api-worksheet
  [module-id :- api/->int worksheet-id :- api/->int modules :- seq? treatment-access-id]
  (let [res (module-content
              treatment-access-id
              module-id
              modules
              #(when (treatment-service/module-has-worksheet? module-id worksheet-id)
                 worksheet-id)
              Worksheet)]
    (http-response/ok res)))

(defapi api-module-content-access
  [module-id :- api/->int content-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (let [_               (get-module module-id modules)      ;; Error if module not available
        module-contents (treatment-service/get-module-contents* [module-id])
        content-ids     (mapv :content-id module-contents)]
    (if (utils/in? content-ids content-id)
      (do
        (treatment-service/register-content-access! content-id module-id treatment-access-id)
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " does not have content " content-id)))))

(defapi api-activate-module
  [module-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (if-let [module (first (filter #(= module-id (:module-id %)) modules))]
    (do
      (when-not (:active? module)
        (treatment-service/activate-module! treatment-access-id module-id))
      (http-response/ok {:result "ok"}))
    (http-response/not-found! (str "No such module " module-id))))

;--------------------
;  CONTENT DATA API
;--------------------

(defapi api-get-module-content-data
  [module-id :- api/->int content-id :- api/->int modules :- seq? treatment-access-id :- int?]
  (let [module         (get-module module-id modules)
        module-content (treatment-service/get-content-in-module module content-id)]
    (if module-content
      (let [content-data (treatment-service/get-module-content-data
                           treatment-access-id
                           module-content)]
        (http-response/ok (or content-data {})))
      (http-response/not-found (str "Module " module-id " does not have content " content-id)))))

(defn data-map->vec
  [data]
  (try
    (reduce-kv
      (fn [init namespace key-values]
        (into init
              (map (fn [[key value]]
                     [(name namespace) (name key) value]) key-values))) [] data)
    (catch Exception _
      (http-response/bad-request! "'data' parameter in wrong format"))))

(defapi api-save-module-content-data
  [module-id :- api/->int content-id :- api/->int data :- map? modules :- seq? treatment-access-id :- int?]
  (let [module   (get-module module-id modules)
        data-vec (data-map->vec data)]
    (if (treatment-service/module-has-content? module-id content-id)
      (do
        (content-data/save-api-content-data! data-vec
                                             treatment-access-id
                                             (get-in module [:content-ns-aliases content-id]))
        (http-response/ok {:result "ok"}))
      (http-response/not-found (str "Module " module-id " does not have content " content-id)))))

(defn- size?
  [v]
  (not (zero? (count v))))

(defapi api-get-content-data
  [namespaces :- [vector? size?] treatment-access-id :- integer?]
  ;; TODO: CHECK IF ALLOWED??
  (http-response/ok (content-data/get-content-data treatment-access-id namespaces)))

(defapi api-save-content-data
  [data :- map? treatment-access-id :- int?]
  (let [data-vec (data-map->vec data)]
    (content-data/save-api-content-data! data-vec
                                         treatment-access-id)
    (http-response/ok {:result "ok"})))