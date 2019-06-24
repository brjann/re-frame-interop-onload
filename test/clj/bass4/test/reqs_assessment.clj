(ns bass4.test.reqs-assessment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer [test-fixtures
                                     fn-not-text?
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
            [bass4.services.attack-detector :as a-d]
            [bass4.instrument.validation :as i-validation]))

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

(deftest answers-validation-fail
  (binding [i-validation/*validate-answers? true]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "validation-test"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome"))
          (visit "/user/assessments")
          (has (some-text? "HAD"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "tjosan" :specifications "tjosan"})
          (has (status? 400))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          ;; Answers validation inactivated
          #_(has (status? 400))
          (has (status? 302))))))


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
        (visit "/user/assessments")
        (has (some-text? "HAD"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "tjosan" :specifications "tjosan"})
        (has (status? 400))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Agoraphobic"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
        ;; Posting answers to instrument not shown yet - advanced stuff!
        (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "AAQ"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
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
          (visit "/user/assessments")
          (has (some-text? "HAD")))
      (-> s1
          (visit "/user/assessments")
          (has (some-text? "HAD")))
      (-> s2
          (visit "/user/assessments")
          (has (some-text? "HAD"))
          ;; Posting answers to wrong instrument. Silently fails but "Something went wrong" is recorded
          ;; in request log.
          (visit "/user/assessments" :request-method :post :params {:instrument-id 6371 :items "{}" :specifications "{}"})
          (has (status? 302)))
      (-> s2
          (visit "/user/assessments")
          (has (some-text? "HAD"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"}))
      (-> s1
          (visit "/user/assessments")
          (has (some-text? "Agoraphobic"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"}))
      (-> s2
          (visit "/user/assessments")
          (has (some-text? "AAQ")))
      (let [s3 (-> *s*
                   (visit "/login" :request-method :post :params {:username user-id :password user-id}))]
        (-> s3
            (has (status? 302))
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Welcome top"))
            (visit "/user/assessments")
            (has (some-text? "AAQ")))
        (-> s1
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"}))
        (-> s3
            (visit "/user/assessments")
            (has (some-text? "PHQ"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"}))
        (-> s2
            (visit "/user/assessments")
            (has (some-text? "Thanks top"))
            (visit "/user/assessments")
            ;; The assessment is now marked as completed and user is redirected...
            (follow-redirect)
            ;; ...to the user page, which redirects to the to-finished page
            (follow-redirect)
            ;; ...which clears the session and redirects to activities-finished page
            (follow-redirect)
            (has (some-text? "finished"))
            (visit "/user")
            (has (status? 403)))))))


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
        (fn-not-text? "welcome 1")
        (visit "/user/assessments" :request-method :post :params {:instrument-id 535693 :items "{}" :specifications "{}"})
        (visit "/user/assessments" :request-method :post :params {:instrument-id 547380 :items "{}" :specifications "{}"})
        (visit "/user/assessments" :request-method :post :params {:instrument-id 547374 :items "{}" :specifications "{}"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "thanks 0"))
        (has (some-text? "thanks 2"))
        (fn-not-text? "thanks 1"))))

(deftest empty-assessment
  (let [user-id (user-service/create-user! 572594 {:Group "572598" :firstname "group-test"})]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302)))))