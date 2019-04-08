(ns bass4.responses.content-example
  (:require
    [bass4.layout :as layout]
    [bass4.services.content-data :as content-data]
    [bass4.api-coercion :as api :refer [defapi]]
    [bass4.module.services :as module-service]))

(defapi edit-example
  [content-id :- api/->int]
  (when-let [content (module-service/get-content content-id)]
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