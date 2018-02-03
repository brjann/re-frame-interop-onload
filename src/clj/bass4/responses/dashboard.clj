(ns bass4.responses.dashboard
  (:require [clj-time.core :as t]
            [bass4.services.bass :as bass]
            [bass4.layout :as layout]
            [bass4.time :as b-time]
            [clojure.tools.logging :as log]))


(defn- new-modules
  [modules last-login]
  (let [tz (bass/time-zone)]
    (filter #(when (:activation-date %)
               (<= 0 (b-time/day-diff-since
                       (t/to-time-zone (:activation-date %) tz)
                       (t/to-time-zone last-login tz))))
            modules)))

(defn treatment-dates
  [treatment]
  (when (get-in treatment [:treatment :access-time-limited])
    [(get-in treatment [:treatment-access :start-date])
     (get-in treatment [:treatment-access :end-date])
     (inc (- (b-time/days-since
               (get-in treatment [:treatment-access :end-date])
               (bass/time-zone))))]))

(defn dashboard
  [user session render-map treatment]
  (let [new-modules (new-modules (:modules (:user-components treatment)) (:last-login-time session))
        [start-date end-date days-remaining] (treatment-dates treatment)]
    (layout/render "dashboard.html"
                   (merge render-map
                          {:user           user
                           :title          "Dashboard"
                           :page-title     "Dashboard"
                           :new-modules    new-modules
                           :start-date     start-date
                           :end-date       end-date
                           :days-remaining days-remaining}))))