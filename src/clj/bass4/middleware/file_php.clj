(ns bass4.middleware.file-php
  (:require [bass4.layout :as layout]
            [bass4.services.bass :as bass]
            [bass4.file-response :as file]))



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
      (let [file (->> (cond
                        (:uploadedfile params)
                        (bass/uploaded-file (:uploadedfile params))

                        (:uid params)
                        (bass/uid-file (:uid params))))]
        (if file
          (file/file-response file)
          (layout/error-404-page "File not found")))
      (handler request))))