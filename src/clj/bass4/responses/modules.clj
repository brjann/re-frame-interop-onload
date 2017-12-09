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

(defn- main-text-renderer
  ;; TODO: How to handle multiple texts
  ;; TODO: How to handle missing texts
  [module]
  (fn [module-render-fn module-contents]
    (let [module-text-id (:content-id (first (:main-texts module-contents)))]
      (module-render-fn
        "main-text.html"
        module-text-id
        {:module-id  module-text-id
         :page-title (:module-name module)}))))


(defn- homework-renderer
  [module submitted]
  (fn [module-render-fn module-contents]
    (if-let [homework-id (:content-id (:homework module-contents))]
      (module-render-fn
        "homework.html"
        homework-id
        {:submitted  submitted
         :page-title (str (i18n/tr [:modules/homework]) " " (:module-name module))})
      (layout/error-404-page (i18n/tr [:modules/no-homework])))))

(defn- worksheet-renderer
  [worksheet-id]
  (fn
    [module-render-fn module-contents]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (module-render-fn
        "worksheet.html"
        worksheet-id)
      (layout/error-404-page (i18n/tr [:modules/no-worksheet])))))

(defn module-content-renderer
  [treatment-access render-fn module module-contents]
  (fn [template content-id & params-map]
    (let [content      (treatment-service/get-content content-id)
          data-name    (:data-name content)
          content-data (content-data/get-content-data
                         (:treatment-access-id treatment-access)
                         (conj (:data-imports content) data-name))
          params       (first params-map)]
      (render-fn
        template
        (merge {:text         (:text content)
                :markdown     (:markdown content)
                :tabbed       (:tabbed content)
                :content-id   content-id
                :data-name    data-name
                :content-data content-data
                :context-menu (context-menu module module-contents)
                :page-title   (:content-name content)}
               params)))))

(defn- module-render-wrapper
  [treatment-access render-fn text-render-fn module]
  (let [module-contents  (treatment-service/get-module-contents (:module-id module))
        module-render-fn (module-content-renderer treatment-access render-fn module module-contents)]
    (text-render-fn module-render-fn module-contents)))

(defn main-text [treatment-access render-fn module]
  (module-render-wrapper treatment-access render-fn (main-text-renderer module) module))

(defn homework [treatment-access render-fn module]
  (module-render-wrapper treatment-access render-fn (homework-renderer module (get-in treatment-access [:submitted-homeworks (:module-id module)])) module))

(defn worksheet [treatment-access render-fn module worksheet-id]
  (module-render-wrapper treatment-access render-fn (worksheet-renderer worksheet-id) module))

(defn modules-list [render-fn modules]
  (render-fn
    "modules-list.html"
    {:modules    modules
     :page-title (i18n/tr [:modules/modules])}))

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
  [treatment-access content-data]
  (when (handle-content-data content-data (:treatment-access-id treatment-access))
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