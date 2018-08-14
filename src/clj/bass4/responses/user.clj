(ns bass4.responses.user
  (:require [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]))


(defn user-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))


(defn privacy-notice-mw
  [handler request]
  (if (= :get (:request-method request))
    (if-let [user (get-in request [:session :user])]
      (do)))
  (handler request))