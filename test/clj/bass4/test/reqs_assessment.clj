(ns bass4.test.reqs-assessment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures not-text?]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(deftest active-assessment
  (with-redefs [auth-service/double-auth-code (constantly "666777")
                t/now (constantly (t/date-time 2017 8 2 0 0 0))]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "one-assessment" :password "one-assessment"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (visit "/double-auth" :request-method :post :params {:code "666777"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Welcome top-priority")))))


(deftest group-assessment
  (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "group-test"})]
    (user/update-user-properties! user-id {:username user-id :password user-id})
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Welcome"))
        (has (some-text? "top top welcome"))
        (visit "/user/")
        (has (some-text? "HAD"))
        (visit "/user/" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Agoraphobic"))
        (visit "/user/" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
        ;; Posting answers to instrument not shown yet - advanced stuff!
        (visit "/user/" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "AAQ"))
        (visit "/user/" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "top top top thanks"))
        (has (some-text? "Thanks top")))))

(deftest group-assessment-concurrent
  (log/debug bass4.middleware.core/*skip-csrf*)
  (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "group-test"})]
    (user/update-user-properties! user-id {:username user-id :password user-id})
    (let [s1 (-> (session (app))
                 (visit "/login" :request-method :post :params {:username user-id :password user-id}))
          s2 (-> (session (app))
                 (visit "/login" :request-method :post :params {:username user-id :password user-id}))]
      (-> s1
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome")))
      (-> s2
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "HAD")))
      (-> s1
          (visit "/user/")
          (has (some-text? "HAD")))
      (-> s2
          (visit "/user/")
          (has (some-text? "HAD"))
          (visit "/user/" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"}))
      (-> s1
          (visit "/user/")
          (has (some-text? "Agoraphobic"))
          (visit "/user/" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"}))
      (-> s2
          (visit "/user/")
          (has (some-text? "AAQ")))
      (let [s3 (-> (session (app))
                   (visit "/login" :request-method :post :params {:username user-id :password user-id}))]
        (-> s3
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "Welcome top"))
            (visit "/user/")
            (has (some-text? "AAQ")))
        (-> s1
            (visit "/user/" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"}))
        (-> s3
            (visit "/user/")
            (has (some-text? "PHQ"))
            (visit "/user/" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"}))
        (-> s2
            (visit "/user/")
            (has (some-text? "Thanks top")))))))


(deftest swallow-texts
  (let [user-id (user/create-user! 547369 {:Group "547387" :firstname "swallow-test"})]
    (user/update-user-properties! user-id {:username user-id :password user-id})
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "welcome 0"))
        (has (some-text? "welcome 2"))
        (not-text? "welcome 1")
        (visit "/user/" :request-method :post :params {:instrument-id 535693 :items "{}" :specifications "{}"})
        (visit "/user/" :request-method :post :params {:instrument-id 547380 :items "{}" :specifications "{}"})
        (visit "/user/" :request-method :post :params {:instrument-id 547374 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "thanks 0"))
        (has (some-text? "thanks 2"))
        (not-text? "thanks 1"))))
