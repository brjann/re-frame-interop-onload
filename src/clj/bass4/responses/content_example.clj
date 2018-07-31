(ns bass4.responses.content-example
  (:require [bass4.services.treatment :as treatment-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.content-data :as content-data]))

(defn edit-example
  [content-id]
  (when-let [content (treatment-service/get-content content-id)]
    (let [data-name    (:data-name content)
          example-data (content-data/get-content-data
                         content-id
                         [data-name]
                         #_(conj (:data-imports content) data-name))]
      (layout/render "content-example.html"
                     {:text         (:text content)
                      :markdown     (:markdown content)
                      :tabbed       (:tabbed content)
                      :content-id   content-id
                      :data-name    data-name
                      :content-data example-data}))))