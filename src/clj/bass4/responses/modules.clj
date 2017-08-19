(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [bass4.services.treatment :as treatment-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.i18n :as i18n]))



(defn modules-list [render-fn modules]
  (render-fn
    "modules-list.html"
    {:modules modules}))


(defn context-menu
  [module-contents]
  (let [homework   (when (:homework module-contents)
                     {:link "homework" :name (i18n/tr [:modules/homework])})
        worksheets (map #(identity {:link (str "worksheet/" (:content-id %)) :name (:content-name %)}) (:worksheets module-contents))]
    (remove nil? (cons homework worksheets))))
;
;(defn- module-main-text
;  [module-contents]
;  (let [module-text-id  (:content-id (first (:main-texts module-contents)))
;        text            (:text (when module-text-id (treatment-service/get-content module-text-id)))]
;    text))
;
;(defn- module-homework
;  [module-contents]
;  (let [module-text-id  (:content-id (:homework module-contents))
;        text            (:text (when module-text-id (treatment-service/get-content module-text-id)))]
;    text))
;
;(defn- module-page [render-fn module-id modules text-fn]
;  (if-let [module (->> (filter #(= module-id (:module-id %)) modules)
;                       (some #(and (:active %) %)))]
;    ;; TODO: How to handle multiple texts
;    (let [module-contents (treatment-service/get-module-contents module-id)
;          module-text-id  (:content-id (first (:main-texts module-contents)))
;          text            (text-fn module-contents)]
;      (render-fn
;        "module.html"
;        {:text         text
;         :context-menu (context-menu module-contents)}))
;    ;; Module not found
;    (layout/error-404-page (i18n/tr [:modules/no-module]))))



(defn main-text-renderer
  ;; TODO: How to handle multiple texts
  ;; TODO: How to handle missing texts
  [module-render-fn module-contents]
  (let [module-text-id (:content-id (first (:main-texts module-contents)))
        text           (:text (when module-text-id (treatment-service/get-content module-text-id)))]
    (module-render-fn
      "module.html"
      {:text text})))

(defn homework-renderer
  [module-render-fn module-contents]
  (if-let [module-text-id (:content-id (:homework module-contents))]
    (module-render-fn
      "homework.html"
      {:text (:text (treatment-service/get-content module-text-id))})
    (layout/error-404-page (i18n/tr [:modules/no-homework]))))


(defn module-render-wrapper
  [render-fn text-render-fn module]
  (let [module-contents  (treatment-service/get-module-contents (:module-id module))
        module-render-fn (fn [template params]
                           (render-fn
                             template
                             (assoc params :context-menu (context-menu module-contents))))]
    (text-render-fn module-render-fn module-contents)))

(defn main-text [render-fn module]
  (module-render-wrapper render-fn main-text-renderer module))

(defn homework [render-fn module]
  (module-render-wrapper render-fn homework-renderer module))