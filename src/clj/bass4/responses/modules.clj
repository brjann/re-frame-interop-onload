(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [bass4.services.treatment :as treatment-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.i18n :as i18n]))


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

(defn- worksheet-renderer
  [worksheet-id]
  (fn
    [module-render-fn module-contents]
    (if (some #(= worksheet-id (:content-id %)) (:worksheets module-contents))
      (module-render-fn
        "worksheet.html"
        {:text (:text (treatment-service/get-content worksheet-id))})
      (layout/error-404-page (i18n/tr [:modules/no-worksheet])))))

(defn- main-text-renderer
  ;; TODO: How to handle multiple texts
  ;; TODO: How to handle missing texts
  [module-render-fn module-contents]
  (let [module-text-id (:content-id (first (:main-texts module-contents)))
        text           (:text (when module-text-id (treatment-service/get-content module-text-id)))]
    (module-render-fn
      "module.html"
      {:text text})))

(defn- homework-renderer
  [module-render-fn module-contents]
  (if-let [module-text-id (:content-id (:homework module-contents))]
    (module-render-fn
      "homework.html"
      {:text (:text (treatment-service/get-content module-text-id))})
    (layout/error-404-page (i18n/tr [:modules/no-homework]))))


(defn- module-render-wrapper
  [render-fn text-render-fn module]
  (let [module-contents  (treatment-service/get-module-contents (:module-id module))
        module-render-fn (fn [template params]
                           (render-fn
                             template
                             (assoc params :context-menu (context-menu (:module-id module) module-contents))))]
    (text-render-fn module-render-fn module-contents)))

(defn main-text [render-fn module]
  (module-render-wrapper render-fn main-text-renderer module))

(defn homework [render-fn module]
  (module-render-wrapper render-fn homework-renderer module))

(defn worksheet [render-fn module worksheet-id]
  (module-render-wrapper render-fn (worksheet-renderer worksheet-id) module))

(defn modules-list [render-fn modules]
  (render-fn
    "modules-list.html"
    {:modules modules}))