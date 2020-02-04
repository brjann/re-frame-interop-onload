(ns bass4.cleaner.files
  (:require [clojure.java.io :as io]
            [bass4.db-config :as db-config]
            [bass4.services.bass :as bass-service]
            [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler])
  (:import (java.io File)))

(def cleanup-dirs ["sessiondata"
                   "../temp"
                   "../temp/uploads"
                   "logs"])

(defn delete-temp-files-task
  [_ local-config _]
  (let [counts (for [dir cleanup-dirs
                     :let [path (binding [db-config/*local-config* local-config]
                                  (bass-service/db-dir dir))]]
                 (if (.isDirectory ^File path)
                   (let [x (filter #(not (bass-service/check-file-age % (* 24 60 60)))
                                   (file-seq path))]
                     (doseq [file x]
                       (io/delete-file file true))
                     (count x))
                   0))]
    {:cycles (reduce + counts)}))

(defstate delete-temp-files-task-starter
  :start (task-scheduler/schedule-db-task! #'delete-temp-files-task
                                           ::task-scheduler/daily-at 3))