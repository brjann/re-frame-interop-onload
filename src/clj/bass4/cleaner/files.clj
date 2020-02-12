(ns bass4.cleaner.files
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.clients.core :as clients]
            [bass4.php-interop :as php-interop]
            [clj-time.core :as t])
  (:import (java.io File)))

(def cleanup-dirs ["sessiondata"
                   "../temp"
                   "../temp/uploads"
                   "logs"])

(defn delete-files!
  [files]
  (let [start-time (t/now)]
    (loop [files files count 0]
      (if (or (nil? (seq files))
              (<= 5 (t/in-minutes (t/interval start-time (t/now)))))
        count
        (do
          (io/delete-file (first files) true)
          (recur (rest files) (inc count)))))))

(defn collect-files
  [local-config cleanup-dirs old-hours]
  (flatten (for [dir cleanup-dirs
                 :let [path (binding [clients/*client-config* local-config]
                              (php-interop/db-dir dir))]]
             (if (.isDirectory ^File path)
               (filter #(and (not (php-interop/check-file-age % (* old-hours 60 60)))
                             (.isFile %))
                       (file-seq path))
               ()))))

(defn delete-temp-files-task
  [_ local-config _]
  (let [files (collect-files local-config cleanup-dirs 24)
        count (delete-files! files)]
    {:cycles count}))

(defstate delete-temp-files-task-starter
  :start (task-scheduler/schedule-db-task! #'delete-temp-files-task
                                           ::task-scheduler/daily-at 3))