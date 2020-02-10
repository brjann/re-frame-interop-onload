(ns bass4.responses.dashboard
  (:require [clj-time.core :as t]
            [bass4.services.bass :as bass]
            [bass4.layout :as layout]
            [bass4.clients.time :as client-time]
            [clojure.tools.logging :as log]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.i18n :as i18n]))


(defn- new-modules
  [modules last-login]
  (seq (filter #(when (:activation-date %)
                  (<= 0 (client-time/day-diff-since-tz
                          (:activation-date %)
                          (or last-login (t/epoch)))))
               modules)))

(defn treatment-dates
  [treatment]
  (when (get-in treatment [:treatment :access-time-limited?])
    [(get-in treatment [:treatment-access :start-date])
     (get-in treatment [:treatment-access :end-date])
     (inc (- (client-time/days-since-tz
               (get-in treatment [:treatment-access :end-date]))))]))

(defapi dashboard
  [user :- map? session :- [:? map?] render-map :- map? treatment :- map?]
  (let [new-modules (new-modules (:modules (:tx-components treatment)) (:last-login-time session))
        [start-date end-date days-remaining] (treatment-dates treatment)]
    (layout/render "dashboard.html"
                   (merge render-map
                          {:user           user
                           :page-title     (i18n/tr [:dashboard/start])
                           :new-modules    new-modules
                           :start-date     start-date
                           :end-date       end-date
                           :days-remaining days-remaining}))))