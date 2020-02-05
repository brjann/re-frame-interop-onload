(ns bass4.client-config
  (:require [mount.core :refer [defstate]]
            [bass4.db.core :as db]
            [bass4.config :as config]))

(def ^:dynamic *local-config* {})

(defstate local-configs
  :start (do db/client-db-configs))

(defn db-setting*
  [client-name-kw setting-keys default]
  (or
    (get-in local-configs (concat [client-name-kw] setting-keys))
    (let [setting (let [x (get-in config/env (into [:db-settings client-name-kw] setting-keys))]
                    (if-not (nil? x)
                      x
                      (let [x (get-in config/env setting-keys)]
                        (if-not (nil? x)
                          x
                          default))))]
      setting)))

(defn db-setting
  ([setting-keys] (db-setting setting-keys nil))
  ([setting-keys default]
   (if (= [:name] setting-keys)
     (:name *local-config*)
     (db-setting* (keyword (:name *local-config*)) setting-keys default))))

(defn debug-mode?
  []
  (or (db-setting [:debug-mode] false) (:dev config/env)))
