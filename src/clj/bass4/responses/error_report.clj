(ns bass4.responses.error-report
  (:require [bass4.services.messages :as messages-service]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.i18n :as i18n]))

(def-api error-report-page [render-map :- map? user :- map?]
  (let [user-id (:user-id user)]
    (layout/render "error-report-tx.html"
                   (merge render-map
                          {:user       user
                           :page-title (i18n/tr [:error-report/report-title])}))))