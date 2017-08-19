(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [bass4.services.treatment :as treatment-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]))



(defn modules-list [render-fn modules]
  (render-fn
    "modules-list.html"
    {:modules modules}))

(defn module [render-fn module-id modules]
  (if-let [module (->> (filter #(= module-id (:module-id %)) modules)
                       (some #(and (:active %) %)))]
    ;; TODO: How to handle multiple texts
    (let [module-contents (treatment-service/get-module-contents module-id)
          module-text-id  (:content-id (first (:main-texts module-contents)))
          text            (:text (when module-text-id (treatment-service/get-content module-text-id)))]
      (log/debug module-contents)
      (log/debug module-text-id)
      (log/debug (treatment-service/get-content module-text-id))
      (render-fn
        "module.html"
        {:text text}))
    ;; TODO: General solution for 404 error or whatever
    ["module.html"
     {}]))