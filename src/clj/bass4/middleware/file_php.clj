(ns bass4.middleware.file-php
  (:require [bass4.layout :as layout]
            [bass4.services.bass :as bass]
            [bass4.file-response :as file]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]))



;; -----------------
;;  FILE.PHP
;; -----------------



(defn File-php
  [handler request]
  (let [uri       (:uri request)
        length    (count uri)
        params    (:params request)
        file-php? (and
                    (< 8 length)
                    (= "File.php" (subs uri (- length 8))))]
    (if file-php?
      (let [response (->> (cond
                            (:uploadedfile params)
                            (let [upload-dir (str (bass/db-dir "upload"))]
                              (http-response/file-response (:uploadedfile params) {:root upload-dir}))

                            (:uid params)
                            (http-response/file-response (str (bass/uid-file (:uid params))))))]
        (log/debug response)
        (if response
          (file/file-headers response)
          (http-response/not-found "File not found")))
      (handler request))))