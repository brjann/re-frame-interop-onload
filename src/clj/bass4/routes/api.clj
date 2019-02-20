(ns bass4.routes.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.responses.user :as user-response]))


(def service-routes
  (api
    {:swagger {:ui   "/swagger-ui"
               :spec "/swagger.json"
               :data {:info {:version     "1.0.0"
                             :title       "BASS API"
                             :description "XXX"}}}}
    (context "/api" []
      (GET "/plus" [] (constantly "x"))
      (context "/user" []
        :dynamic true
        (context "/tx" [:as request]
          :middleware [user-response/treatment-mw]
          (GET "/" []
            (http-response/ok (get-in request [:db]))))))))



(api
  (context "/tx" [:as request]
    :middleware [user-response/treatment-mw]
    (GET "/" []
      (http-response/ok (get-in request [:db])))))