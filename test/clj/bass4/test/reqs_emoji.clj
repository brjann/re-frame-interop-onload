(ns bass4.test.reqs-emoji
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [ring.mock.request :as mock]
            [bass4.test.core :refer :all]
            [clojure.string :as str]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest test-csrf
  (let [param "metal\uD83D\uDE0A\uD83D\uDC4Dlica"
        bass  (app)]
    (is (str/includes? (:body (bass (mock/request :get "/debug/params" {"hejsan" param})))
                       "metal��lica"))
    (is (str/includes? (:body (bass (mock/request :get "/debug/query-params" {"hejsan" param})))
                       "metal��lica"))

    (is (str/includes? (:body (bass (-> (mock/request :post "/debug/params")
                                        (mock/body {"hejsan" param}))))
                       "metal��lica"))
    (is (str/includes? (:body (bass (-> (mock/request :post "/debug/form-params")
                                        (mock/body {"hejsan" param}))))
                       "metal��lica"))

    (is (str/includes? (:body (bass (-> (mock/request :post "/debug/params")
                                        (mock/json-body {"hejsan" param}))))
                       "metal��lica"))
    (is (str/includes? (:body (bass (-> (mock/request :post "/debug/body-params")
                                        (mock/json-body {"hejsan" param}))))
                       "metal��lica"))))