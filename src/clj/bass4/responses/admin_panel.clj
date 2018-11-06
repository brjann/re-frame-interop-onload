(ns bass4.responses.admin-panel
  (:require [clj-time.core :as t]
            [bass4.services.bass :as bass]
            [bass4.layout :as layout]
            [bass4.time :as b-time]
            [clojure.tools.logging :as log]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.i18n :as i18n]
            [mount.core :as mount]
            [ring.util.http-response :as http-response]))



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
  (layout/render "states.html" {:states (mapv #(subs % 2) (mount/find-all-states))}))