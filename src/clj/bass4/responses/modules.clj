(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as http-response]
            [bass4.http-utils :refer [url-escape]]
            [bass4.services.treatment :as treatment-service]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.content-data :as content-data]
            [bass4.i18n :as i18n]
            [bass4.services.content-data :as content-data-service]
            [bass4.api-coercion :as api :refer [def-api]]))

(defn- context-menu
  [module module-contents]
  (let [file-php        (fn [content]
                          (str "File.php?uploadedfile="
                               (url-escape (:file-path content))))
        base-path       (str "/user/module/" (:module-id module))
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
  (let [content      (treatment-service/get-content content-id)
        data-name    (:data-name content)
        content-data (content-data/get-content-data
                       (:treatment-access-id treatment-access)
                       (conj (:data-imports content) data-name))
        params       (first params-map)]
    (treatment-service/register-content-access!
      content-id
      (:module-id module)
      (:treatment-access-id treatment-access))
    (layout/render
      template
      (merge render-map
             {:text         (:text content)
              :file-path    (:file-path content)
              :markdown     (:markdown content)
              :tabbed       (:tabbed content)
              :show-example (:show-example content)
              :content-id   content-id
              :data-name    data-name
              :content-data content-data
              :context-menu (context-menu module module-contents)
              :page-title   (:content-name content)}
             params))))

(def-api main-text
  [treatment-access :- map? render-map :- map? module :- map?]
  (let [module-contents (treatment-service/get-categorized-module-contents (:module-id module))
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

(def-api homework
  [treatment-access :- map? render-map :- map? module :- map?]
  (let [module-contents (treatment-service/get-categorized-module-contents (:module-id module))]
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
      (layout/error-404-page (i18n/tr [:modules/no-homework])))))

(def-api worksheet
  [treatment-access :- map? render-map :- map? module :- map? worksheet-id :- api/int!]
  (let [module-contents (treatment-service/get-categorized-module-contents (:module-id module))]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (module-content-renderer
        treatment-access
        render-map
        module
        module-contents
        "module-worksheet.html"
        worksheet-id)
      (layout/error-404-page (i18n/tr [:modules/no-worksheet])))))

(def-api worksheet-example
  [module :- map? worksheet-id :- api/int! return-path :- api/URL?]
  (let [module-contents (treatment-service/get-categorized-module-contents (:module-id module))]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (let [content      (treatment-service/get-content worksheet-id)
            data-name    (:data-name content)
            example-data (content-data/get-content-data
                           worksheet-id
                           [data-name])]
        (layout/render "module-worksheet-example.html"
                       {:return-path  return-path
                        :text         (:text content)
                        :markdown     (:markdown content)
                        :tabbed       (:tabbed content)
                        :content-id   worksheet-id
                        :data-name    data-name
                        :content-data example-data}))
      (layout/error-404-page (i18n/tr [:modules/no-worksheet])))))

(def-api modules-list [render-map :- map? modules :- seq? treatment-access-id :- integer?]
  (let [module-contents      (treatment-service/get-module-contents-with-update-time
                               (mapv :module-id modules)
                               treatment-access-id)
        modules-with-content (mapv #(assoc % :contents (get module-contents (:module-id %))) modules)]
    (layout/render
      "modules-list.html"
      (merge render-map
             {:modules    modules-with-content
              :page-title (i18n/tr [:modules/modules])}))))



;--------------
; CONTENT DATA
;--------------

(defn- handle-content-data
  [data-map treatment-access-id]
  (if (map? data-map)
    (do
      (content-data-service/save-content-data!
        data-map
        treatment-access-id)
      true)
    (layout/throw-400!)))

(def-api save-worksheet-example-data
  [content-id :- api/int! content-data :- api/JSON-map!]
  (when (handle-content-data content-data content-id)
    (http-response/found "reload")))

(def-api save-worksheet-data
  [treatment-access-id :- integer? content-data :- api/JSON-map!]
  (when (handle-content-data content-data treatment-access-id)
    (http-response/found "reload")))

(def-api save-main-text-data
  [treatment-access :- map? content-data :- api/JSON-map!]
  (when (handle-content-data content-data (:treatment-access-id treatment-access))
    (http-response/ok {})))

(def-api save-homework
  [treatment-access :- map? module :- map? content-data :- api/JSON-map! submit? :- api/bool!]
  (when (handle-content-data content-data (:treatment-access-id treatment-access))
    (when submit?
      (treatment-service/submit-homework! treatment-access module))
    (http-response/found "reload")))

(def-api retract-homework
  [treatment-access :- map? module :- map?]
  (if-let [submitted (get-in treatment-access [:submitted-homeworks (:module-id module)])]
    (do
      (when (not (:ok? submitted))
        (treatment-service/retract-homework! submitted module))
      (http-response/found "reload"))
    (layout/throw-400!)))