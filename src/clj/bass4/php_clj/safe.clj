(ns bass4.php-clj.safe
  (:require [bass4.php_clj.reader :as r]
            [bass4.php_clj.core :as php_clj]
            [bass4.mailer :as mail]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]))

(defn php->clj
  "Converts serialized PHP into equivalent Clojure data structures.

  Note that one-dimensional PHP arrays (those with consecutive indices starting
  at 0 such as array(1, 2, 3)) will be converted to vectors while all others
  will be converted to ordered maps therefore preserving insertion order.

  Example: (php->clj \"a:2:{i:0;i:1;i:1;i:2;}\")"
  [php]
  (try
    (php_clj/php->clj php true)
    (catch Exception e
      (mail/mail!
        (env :error-email)
        "Unserialize error in BASS4"
        "An php unserialize error happened in BASS4. See log for details")
      (log/error (str "Failed php unserialize\n" php "\n" e)))))