(ns bass4.test.reqs-set-password
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.handler :refer :all]
            [bass4.test.core :refer :all]
            [bass4.db.core :as db]
            [bass4.password.set-responses :as set-pw-response]
            [bass4.password.set-services :as set-pw-service])
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
      (has (status? 404))
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
