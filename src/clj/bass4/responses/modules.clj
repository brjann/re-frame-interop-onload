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

(defn module [render-fn module-id modules]
  (if-let [module (->> (filter #(= module-id (:module-id %)) modules)
                       (some #(and (:active %) %)))]
    ;; TODO: How to handle multiple texts
    (let [module-contents (treatment-service/get-module-contents module-id)
          module-text-id  (:content-id (first (:main-texts module-contents)))
          text            (:text (when module-text-id (treatment-service/get-content module-text-id)))]
      (render-fn
        "module.html"
        {:text         text
         :context-menu (context-menu module-contents)}))
    ;; Module not found
    (layout/error-404-page (i18n/tr [:modules/no-module]))))