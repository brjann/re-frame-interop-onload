(ns bass4.test.reqs-url
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(deftest request-url-encode
  (-> (session (app))
      (visit "/debug/encode")
      (follow-redirect)
      (follow-redirect)
      (has (some-text? "{:arg1 \"val1\", :arg2 \"val2\", :arg3 \"path/to/resource\"}"))))
