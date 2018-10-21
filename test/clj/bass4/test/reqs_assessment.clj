(ns bass4.test.reqs-assessment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     log-return
                                     log-body
                                     log-headers
                                     log-status
                                     disable-attack-detector
                                     *s*]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.services.attack-detector :as a-d]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest active-assessment
  (with-redefs [auth-service/double-auth-code (constantly "666777")
                t/now (constantly (t/date-time 2017 8 2 0 0 0))]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "one-assessment" :password "one-assessment"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (visit "/double-auth" :request-method :post :params {:code "666777"})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Welcome top-priority")))))


(deftest group-assessment
  (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "group-test"})]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Welcome"))
        (has (some-text? "top top welcome"))
        (visit "/assessments")
        (has (some-text? "HAD"))
        (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "tjosan" :specifications "tjosan"})
        (has (status? 400))
        (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Agoraphobic"))
        (visit "/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
        ;; Posting answers to instrument not shown yet - advanced stuff!
        (visit "/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "AAQ"))
        (visit "/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "top top top thanks"))
        (has (some-text? "Thanks top")))
    (is (= {:assessment-id 536106, :assessment-index 1} (db/get-last-assessment {:user-id user-id})))
    (is (= 7 (count (db/get-completed-answers {:user-id user-id}))))
    (is (= #{536112 536113} (set (map :assessment-id (db/get-assessments-with-date {:user-id user-id})))))))

(deftest group-assessment-concurrent
  (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "group-test-concurrent"})]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (let [s1 (-> *s*
                 (visit "/login" :request-method :post :params {:username user-id :password user-id}))
          s2 (-> *s*
                 (visit "/login" :request-method :post :params {:username user-id :password user-id}))]
      (-> s1
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome")))
      (-> s2
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome"))
          (visit "/assessments")
          (has (some-text? "HAD")))
      (-> s1
          (visit "/assessments")
          (has (some-text? "HAD")))
      (-> s2
          (visit "/assessments")
          (has (some-text? "HAD"))
          ;; Posting answers to wrong instrument. Silently fails but "Something went wrong" is recorded
          ;; in request log.
          (visit "/assessments" :request-method :post :params {:instrument-id 6371 :items "{}" :specifications "{}"})
          (has (status? 302)))
      (-> s2
          (visit "/assessments")
          (has (some-text? "HAD"))
          (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"}))
      (-> s1
          (visit "/assessments")
          (has (some-text? "Agoraphobic"))
          (visit "/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"}))
      (-> s2
          (visit "/assessments")
          (has (some-text? "AAQ")))
      (let [s3 (-> *s*
                   (visit "/login" :request-method :post :params {:username user-id :password user-id}))]
        (-> s3
            (has (status? 302))
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Welcome top"))
            (visit "/assessments")
            (has (some-text? "AAQ")))
        (-> s1
            (visit "/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"}))
        (-> s3
            (visit "/assessments")
            (has (some-text? "PHQ"))
            (visit "/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"}))
        (-> s2
            (visit "/assessments")
            (has (some-text? "Thanks top"))
            (visit "/assessments")
            ;; The assessment is now marked as completed and user is redirected...
            (follow-redirect)
            ;; ...to the user page, which redirects to the login because user is not in treatment
            (follow-redirect)
            (has (some-text? "Login")))))))


(deftest swallow-texts
  (let [user-id (user-service/create-user! 547369 {:Group "547387" :firstname "swallow-test"})]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "welcome 0"))
        (has (some-text? "welcome 2"))
        (not-text? "welcome 1")
        (visit "/assessments" :request-method :post :params {:instrument-id 535693 :items "{}" :specifications "{}"})
        (visit "/assessments" :request-method :post :params {:instrument-id 547380 :items "{}" :specifications "{}"})
        (visit "/assessments" :request-method :post :params {:instrument-id 547374 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "thanks 0"))
        (has (some-text? "thanks 2"))
        (not-text? "thanks 1"))))

(deftest empty-assessment
  (let [user-id (user-service/create-user! 572594 {:Group "572598" :firstname "group-test"})]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302)))))