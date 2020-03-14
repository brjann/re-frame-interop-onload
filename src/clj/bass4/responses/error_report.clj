(ns bass4.responses.error-report
  (:require [ring.util.http-response :as http-response]
            [bass4.services.error-report :as error-report-service]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.i18n :as i18n]))

(def ^:const max-chars 2000)

(defapi error-report-page [render-map :- map? user :- map?]
  (let [user-id (:user-id user)]
    (layout/render "error-report-tx.html"
                   (merge render-map
                          {:user       user
                           :page-title (i18n/tr [:error-report/page-title])
                           :max-chars  max-chars}))))

(defapi handle-error-report [user :- map? error-description :- [[api/str? 1 (+ max-chars 500)]]]
  (let [user-id (:user-id user)]
    (error-report-service/create-error-report-flag! user-id error-description)
    (http-response/ok "ok")))