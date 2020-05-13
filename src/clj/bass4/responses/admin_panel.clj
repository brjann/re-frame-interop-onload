(ns bass4.responses.admin-panel
  (:require [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [mount.core :as mount]
            [bass4.middleware.lockdown :as lockdown]))

(defapi reset-state
  [state-name :- [[api/str? 1 100]]]
  (let [states     (mount/find-all-states)
        state-name (str "#'" state-name)]
    (if (some #{state-name} states)
      (do
        (mount.core/stop state-name)
        (mount.core/start state-name)
        (layout/text-response (str "Successfully restarted state " state-name)))
      (layout/text-response (str "Couldn't find state " state-name \newline states)))
    #_(http-response/found "states")))

(defapi states-page []
  (layout/render "states.html"
                 {:states       (mapv #(subs % 2) (mount/find-all-states))
                  :locked-down? @lockdown/locked-down?}))

(defapi lock-down!
  []
  (reset! lockdown/locked-down? true)
  (layout/text-response (str "BASS is locked down!")))

(defapi cancel-lockdown!
  []
  (reset! lockdown/locked-down? false)
  (layout/text-response (str "BASS is not locked down!")))