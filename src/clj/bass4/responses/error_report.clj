(ns bass4.responses.error-report
  (:require [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [bass4.services.error-report :as error-report-service]
            [schema.core :as s]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.i18n :as i18n]))

(def ^:const max-chars 2000)

(def-api error-report-page [render-map :- map? user :- map?]
  (let [user-id (:user-id user)]
    (layout/render "error-report-tx.html"
                   (merge render-map
                          {:user       user
                           :page-title (i18n/tr [:error-report/page-title])
                           :max-chars  max-chars}))))

(def-api handle-error-report [user :- map? error-description :- api/str+!]
  (let [user-id (:user-id user)]
    (if (> (+ max-chars 500) (count error-description))
      (do
        (error-report-service/create-error-report-flag! user-id error-description)
        (http-response/ok "ok"))
      (layout/error-400-page))))