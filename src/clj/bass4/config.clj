(ns bass4.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :refer [args defstate]]
            [clojure.java.io :as io]))

;; Only one config file can be read using the :file arg to load-config, which
;; seems to be .lein-env in development mode (which obtains config from
;; profiles.clj). Therefore we will check for the existence of local.edn,
;; which will hold production settings. If it exists, we include it in the load
;; config

(def ^:dynamic test-mode? false)

(def ^:const defaults {:timeout-hard       (* 4 60 60)
                       :timeout-hard-short (* 2 60 60)
                       :timeout-hard-soon  (* 1 60 60)
                       :timeout-soft       (* 1 60 60)})

(defn merge-args
  []
  (let [config   [(args)
                  (source/from-system-props)
                  (source/from-env)]
        ; Split filename to avoid refactoring when filename changes
        filename (str (System/getProperty "user.dir") "/local" ".edn")]
    [:merge (conj config (merge defaults
                                (when (.exists (io/file filename))
                                  (source/from-file filename))))]))

(defstate env :start (apply load-config (merge-args)))
