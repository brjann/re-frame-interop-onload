(ns bass4.captcha
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]))

(def captcha-chars [1 2 3 4 6 7 8 9])

(defn captcha-number [length]
  (clojure.string/join
    ""
    (map
      #(get captcha-chars %1)
      (repeatedly length #(rand-int (- (count captcha-chars) 1))))))

(defn captcha!
  []
  (let [captcha-path (io/file (env :bass-path) "system/CaptchaGenerator.php")
        filename     (str "captcha_" (java.util.UUID/randomUUID) ".jpg")
        path         (io/file (env :bass-path) "projects/temp" filename)
        digits       (captcha-number 5)
        args         {"filename" (str path) "digits" digits}
        res          (shell/sh "php" (str captcha-path) :in (clj->php args))]
    res
    (when (not= 0 (:exit res))
      (throw (Exception. (str (:out res)))))
    {:filename filename :digits digits}))