(ns bass4.test.reqs-set-password
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.handler :refer :all]
            [bass4.test.core :refer :all]
            [bass4.db.core :as db]
            [bass4.password.set-responses :as set-pw-response]
            [bass4.password.set-services :as set-pw-service]
            [bass4.test.reqs-embedded :as reqs-embedded]
            [bass4.utils :as utils]
            [bass4.now :as now]
            [clojure.java.jdbc :as jdbc]
            [bass4.php-interop :as php-interop])
  (:import (java.net URL)))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

;; ----------------
;;   SET PASSWORD
;; ----------------

(deftest invalid
  (-> *s*
      (visit "/p/xx")
      (has (status? 400))
      (visit (str "/p/" (apply str (repeat set-pw-service/uid-length "x"))))
      (has (status? 404))))

(deftest set-password
  (let [user-id  (create-user-with-password! {:email      "example@example.com"
                                              "smsnumber" "00"})
        link     (set-pw-response/link! db/*db* user-id)
        path     (.getPath (URL. link))
        password "Metallica2020"]
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (visit "/logout")
        (visit path)
        (has (status? 200))
        (visit path :request-method :post :params {"password" "X"})
        (has (status? 400))
        (visit path :request-method :post :params {"password" password})
        (has (status? 200))
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 422))
        (visit "/login" :request-method :post :params {:username user-id :password password})
        (has (status? 302)))))

(deftest expired
  (fix-time
    (let [user-id (create-user-with-password!)
          link    (set-pw-response/link! db/*db* user-id)
          path    (.getPath (URL. link))]
      (-> *s*
          (visit path)
          (has (status? 200))
          (advance-time-d! 2)
          ;; Seems to be some threshold issue here so need to +/- 1 sec, else random failures.
          (advance-time-s! -1)
          (visit path)
          (has (status? 200))
          (advance-time-s! 2)
          (visit path)
          (has (status? 404))))))

;; ----------------
;;  PASSWORD LINK
;; ----------------
(deftest create-link
  (let [user-id        (create-user-with-password! {:email      "example@example.com"
                                                    "smsnumber" "00"})
        php-session-id (reqs-embedded/get-php-session-id)
        now            (utils/to-unix (now/now))]
    (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id "UserId" 110 "LastActivity" now "SessionStart" now})
    (let [uid        (php-interop/uid-for-data! {:user-id        110
                                                 :path           (str "iframe/send-password-link/" user-id)
                                                 :php-session-id php-session-id})
          sms-length (- 150 (set-pw-response/link-length db/*db*))]
      (-> *s*
          (visit (str "/embedded/create-session?uid=" uid))
          (visit (str "/embedded/iframe/send-password-link/" user-id))
          (has (status? 200))
          ;; Wrong type (caps)
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "SMS"
                                                                                                     :message "{LINK}"})
          (has (status? 400))
          ;; --- SMS ---
          ;; OK
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "sms"
                                                                                                     :message "{LINK}"})
          (has (status? 200))
          ;; Missing {LINK}
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "sms"
                                                                                                     :message "LINK"})
          (has (status? 400))

          ;; Not too long
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post
                 :params {:type    "sms"
                          :message (str "{LINK}"
                                        (apply str (repeat sms-length "X")))})
          (has (status? 200))

          ;; Too long
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post
                 :params {:type    "sms"
                          :message (str "{LINK}"
                                        (apply str (repeat (inc sms-length) "X")))})
          (has (status? 400))

          ;; No SMS number
          (pass-by (jdbc/execute! db/*db* ["UPDATE c_participant SET SMSNumber = '' WHERE ObjectId = ?" user-id]))
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "sms"
                                                                                                     :message "{LINK}"})

          (has (status? 400))
          ;; --- EMAIL ---
          ;; OK
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "email"
                                                                                                     :message "{LINK}"
                                                                                                     :subject "XXX"})
          (has (status? 200))
          ;; Missing {LINK}
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "email"
                                                                                                     :message "LINK"
                                                                                                     :subject "XXX"})
          (has (status? 400))
          ;; Missing subject
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "email"
                                                                                                     :message "{LINK}"
                                                                                                     :subject ""})
          (has (status? 400))
          ;; No email
          (pass-by (jdbc/execute! db/*db* ["UPDATE c_participant SET Email = '' WHERE ObjectId = ?" user-id]))
          (visit (str "/embedded/iframe/send-password-link/" user-id) :request-method :post :params {:type    "email"
                                                                                                     :message "{LINK}"
                                                                                                     :subject "XXX"})
          (has (status? 400))))))