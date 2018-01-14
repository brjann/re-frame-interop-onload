(ns bass4.responses.user
  (:require [ring.util.http-response :as response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.request-state :as request-state]
            [schema.core :as s]
            [bass4.layout :as layout]))


(defn user-page-renderer
  [treatment path]
  (fn [template params]
    (let [user-components (:user-components treatment)]
      (layout/render
        template
        (merge params
               user-components
               {:path          path
                :new-messages? (:new-messages? treatment)})))))