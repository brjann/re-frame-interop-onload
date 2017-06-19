(ns bass4.test.requests
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

#_(deftest request-x
  (-> (session (app))
      (visit "/user/messages")
      (has (status? 403))
      (visit "/debug/set-session" :params {:identity 535899 :double-auth-code "666-666-666"})
      (visit "/user/messages")
      (follow-redirect)
      (has (some-text? "666-666-666"))))

(deftest request-authentication
  (-> (session (app))
      (visit "/user/messages")
      (has (status? 403))
      (visit "/debug/set-session" :params {:identity 535899 :double-auth-code "666-666-666"})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "666-666-666"))
      (visit "/double-auth")
      (has (some-text? "666-666-666"))))


(def x 89)