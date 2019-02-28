(ns bass4.responses.content-example
  (:require [bass4.services.treatment :as treatment-service]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.content-data :as content-data]
            [bass4.api-coercion :as api :refer [defapi]]))

(defapi edit-example
  [content-id :- api/->int]
  (when-let [content (treatment-service/get-content content-id)]
    (let [namespace    (:namespace content)
          example-data (content-data/get-content-data
                         content-id
                         [namespace]
                         #_(conj (:data-imports content) namespace))]
      (layout/render "content-example-editor.html"
                     {:text         (:text content)
                      :markdown?    (:markdown? content)
                      :tabbed?      (:tabbed? content)
                      :content-id   content-id
                      :namespace    namespace
                      :content-data example-data}))))