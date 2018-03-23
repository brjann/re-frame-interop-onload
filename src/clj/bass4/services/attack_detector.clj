(ns bass4.services.attack-detector
  (:require [bass4.mailer :refer [mail!]]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]))

