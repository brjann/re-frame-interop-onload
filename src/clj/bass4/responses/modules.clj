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
  [module-id module-contents]
  (let [base-path  (str "/user/module/" module-id)
        main-text  {:link (str base-path "/")
                    :name (i18n/tr [:modules/module-text])}
        homework   (when (:homework module-contents)
                     {:link (str base-path "/homework")
                      :name (i18n/tr [:modules/homework])})
        worksheets (map #(identity {:link (str base-path "/worksheet/" (:content-id %))
                                    :name (:content-name %)})
                        (:worksheets module-contents))]
    (remove nil? (into [main-text homework] worksheets))))

(defn- main-text-renderer
  ;; TODO: How to handle multiple texts
  ;; TODO: How to handle missing texts
  [module-render-fn module-contents]
  (let [module-text-id (:content-id (first (:main-texts module-contents)))]
    (module-render-fn
      "module.html"
      module-text-id)))

(defn- homework-renderer
  [module-render-fn module-contents]
  (if-let [homework-id (:content-id (:homework module-contents))]
    (module-render-fn
      "homework.html"
      homework-id)
    (layout/error-404-page (i18n/tr [:modules/no-homework]))))

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
  (fn [template content-id]
    (let [content      (treatment-service/get-content content-id)
          data-name    (:data-name content)
          content-data (content-data/get-content-data
                         (:treatment-access-id treatment-access)
                         (conj (:data-imports content) data-name))]
      (render-fn
        template
        {:text         (:text content)
         :markdown     (:markdown content)
         :data-name    data-name
         :content-data content-data
         :context-menu (context-menu (:module-id module) module-contents)}))))

(defn- module-render-wrapper
  [treatment-access render-fn text-render-fn module]
  (let [module-contents  (treatment-service/get-module-contents (:module-id module))
        module-render-fn (module-content-renderer treatment-access render-fn module module-contents)]
    (text-render-fn module-render-fn module-contents)))

(defn main-text [treatment-access render-fn module]
  (module-render-wrapper treatment-access render-fn main-text-renderer module))

(defn homework [treatment-access render-fn module]
  (module-render-wrapper treatment-access render-fn homework-renderer module))

(defn worksheet [treatment-access render-fn module worksheet-id]
  (module-render-wrapper treatment-access render-fn (worksheet-renderer worksheet-id) module))

(defn modules-list [render-fn modules]
  (render-fn
    "modules-list.html"
    {:modules modules}))