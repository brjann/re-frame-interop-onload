(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [bass4.services.treatment :as treatment-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.content-data :as content-data]
            [bass4.i18n :as i18n]
            [bass4.services.content-data :as content-data-service]))


(defn- context-menu
  [module module-contents]
  (let [base-path  (str "/user/module/" (:module-id module))
        main-text  {:link (str base-path "/")
                    :name (i18n/tr [:modules/module-text])}
        homework   (when (:homework module-contents)
                     {:link (str base-path "/homework")
                      :name (i18n/tr [:modules/homework])})
        worksheets (map #(identity {:link (str base-path "/worksheet/" (:content-id %))
                                    :name (:content-name %)})
                        (:worksheets module-contents))]
    (merge {:items (remove nil? (into [main-text homework] worksheets))}
           {:title (:module-name module)})))


(defn module-content-renderer
  [treatment-access render-map module module-contents template content-id & params-map]
  (let [content      (treatment-service/get-content content-id)
        data-name    (:data-name content)
        content-data (content-data/get-content-data
                       (:treatment-access-id treatment-access)
                       (conj (:data-imports content) data-name))
        params       (first params-map)]
    (layout/render
      template
      (merge render-map
             {:text         (:text content)
              :markdown     (:markdown content)
              :tabbed       (:tabbed content)
              :show-example (:show-example content)
              :content-id   content-id
              :data-name    data-name
              :content-data content-data
              :context-menu (context-menu module module-contents)
              :page-title   (:content-name content)}
             params))))

(defn main-text [treatment-access render-map module]
  (let [module-contents (treatment-service/get-module-contents (:module-id module))
        module-text-id  (:content-id (first (:main-texts module-contents)))]
    (module-content-renderer
      treatment-access
      render-map
      module
      module-contents
      "module-main-text.html"
      module-text-id
      {:module-id  module-text-id
       :page-title (:module-name module)})))

(defn homework [treatment-access render-map module]
  (let [module-contents (treatment-service/get-module-contents (:module-id module))]
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


(defn worksheet [treatment-access render-map module worksheet-id]
  (let [module-contents (treatment-service/get-module-contents (:module-id module))]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (module-content-renderer
        treatment-access
        render-map
        module
        module-contents
        "module-worksheet.html"
        worksheet-id)
      (layout/error-404-page (i18n/tr [:modules/no-worksheet])))))

(defn worksheet-example [module worksheet-id return-path]
  (let [module-contents (treatment-service/get-module-contents (:module-id module))]
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

(defn modules-list [render-map modules]
  (let [module-contents      (treatment-service/get-multiple-module-contents (mapv :module-id modules))
        modules-with-content (mapv #(assoc % :contents (get module-contents (:module-id %))) modules)]
    (layout/render
      "modules-list.html"
      (merge render-map
             {:modules    modules-with-content
              :page-title (i18n/tr [:modules/modules])}))))

(defn- handle-content-data
  [data-map treatment-access-id]
  (if (map? data-map)
    (do
      (content-data-service/save-content-data!
        data-map
        treatment-access-id)
      true)
    (layout/throw-400!)))

(defn save-worksheet-data
  [treatment-access-id content-data]
  (when (handle-content-data content-data treatment-access-id)
    (response/found "reload")))

(defn save-main-text-data
  [treatment-access content-data]
  (when (handle-content-data content-data (:treatment-access-id treatment-access))
    (response/ok {})))

(defn save-homework
  [treatment-access module content-data submit?]
  (when (handle-content-data content-data (:treatment-access-id treatment-access))
    (when submit?
      (treatment-service/submit-homework! treatment-access module))
    (response/found "reload")))

(defn retract-homework
  [treatment-access module]
  (if-let [submitted (get-in treatment-access [:submitted-homeworks (:module-id module)])]
    (when (not (:ok submitted))
      (treatment-service/retract-homework! submitted module)
      (response/found "reload"))
    (layout/throw-400!)))