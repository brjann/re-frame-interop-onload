(ns bass4.middleware.file-php
  (:require [bass4.file-response :as file]
            [ring.util.http-response :as http-response]
            [bass4.php-interop :as php-interop]))



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
      (let [response (try
                       (cond
                         (:uploadedfile params)
                         (let [upload-dir (str (php-interop/db-dir "upload"))]
                           (http-response/file-response (:uploadedfile params) {:root upload-dir}))

                         (:uid params)
                         (http-response/file-response (str (php-interop/uid-file (:uid params)))))

                       (catch Exception _))]
        (if response
          (file/file-headers response)
          (->
            (http-response/not-found "File not found")
            (http-response/content-type "text/plain; charset=utf-8"))))
      (handler request))))