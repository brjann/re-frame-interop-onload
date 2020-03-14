(ns bass4.module.responses
  (:require [ring.util.http-response :as http-response]
            [clojure.string :as str]
            [bass4.http-utils :refer [url-escape]]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [bass4.services.content-data :as content-data-service]
            [bass4.http-errors :as http-errors]
            [bass4.module.services :as module-service]
            [bass4.module.module-content :as module-content]))


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
  (let [module-content (module-content/content-in-module module content-id)
        namespace      (:namespace module-content)
        content-data   (module-content/module-content-data
                         (:treatment-access-id treatment-access)
                         module-content)
        params         (first params-map)]
    (module-service/register-content-access!
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
  (let [module-contents (module-content/contents-by-category module)
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
  (let [module-contents (module-content/contents-by-category module)]
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
  (let [module-contents (module-content/contents-by-category module)]
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
  (let [module-contents (module-content/contents-by-category module)]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (let [content      (module-service/get-content worksheet-id)
            namespace    (:namespace content)
            example-data (content-data-service/get-content-data
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
             {:modules    (module-content/assoc-content-info modules treatment-access-id)
              :page-title (i18n/tr [:modules/modules])}))))

(defapi view-user-content
  [treatment-access-id :- api/->int module-id :- api/->int content-id :- api/->int]
  (let [module         (module-service/get-module module-id)
        module-content (module-content/content-in-module module content-id)
        namespace      (:namespace module-content)
        content-data   (module-content/module-content-data treatment-access-id module-content)]
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
  (let [main-text-id (module-service/get-module-main-text-id (:module-id module))]
    (when (handle-content-data content-data
                               treatment-access-id
                               (get-in module [:content-ns-aliases main-text-id]))
      (http-response/ok {}))))

(defapi save-homework
  [treatment-access-id :- integer? module :- map? content-data :- [api/->json map?] submit? :- api/->bool]
  (let [homework-id (module-service/get-module-homework-id (:module-id module))]
    (when (handle-content-data content-data
                               treatment-access-id
                               (get-in module [:content-ns-aliases homework-id]))
      (when submit?
        (module-service/submit-homework! treatment-access-id module))
      (http-response/found "reload"))))

(defapi retract-homework
  [treatment-access :- map? module :- map?]
  (if-let [submitted (get-in treatment-access [:submitted-homeworks (:module-id module)])]
    (do
      (when (not (:ok? submitted))
        (module-service/retract-homework! treatment-access module))
      (http-response/found "reload"))
    (http-errors/throw-400!)))
