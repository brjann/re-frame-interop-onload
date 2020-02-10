(ns bass4.cleaner.files
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.clients :as clients]
            [bass4.php-interop :as php-interop])
  (:import (java.io File)))

(def cleanup-dirs ["sessiondata"
                   "../temp"
                   "../temp/uploads"
                   "logs"])

(defn delete-temp-files-task
  [_ local-config _]
  (let [counts (for [dir cleanup-dirs
                     :let [path (binding [clients/*local-config* local-config]
                                  (php-interop/db-dir dir))]]
                 (if (.isDirectory ^File path)
                   (let [x (filter #(not (php-interop/check-file-age % (* 24 60 60)))
                                   (file-seq path))]
                     (doseq [file x]
                       (io/delete-file file true))
                     (count x))
                   0))]
    {:cycles (reduce + counts)}))

(defstate delete-temp-files-task-starter
  :start (task-scheduler/schedule-db-task! #'delete-temp-files-task
                                           ::task-scheduler/daily-at 3))