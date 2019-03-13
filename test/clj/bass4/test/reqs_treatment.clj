(ns bass4.test.reqs-treatment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.db.core :as db]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     disable-attack-detector
                                     *s*
                                     modify-session
                                     log-body
                                     log-headers
                                     log-response]]
            [clj-time.core :as t]
            [clojure.data.json :as json]
            [bass4.responses.error-report :as error-report-response]
            [bass4.services.user :as user-service]
            [bass4.services.privacy :as privacy-service])
  (:import (java.util UUID)))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-messages
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    (-> *s*
        (modify-session {:user-id 549821 :double-authed? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (not-text? "New message")
        (visit "/user/tx/messages" :request-method :post :params {:message "xxx"})
        (has (status? 404))
        (visit "/user/tx/messages-save-draft" :request-method :post :params {:message "xxx"})
        (has (status? 404)))
    (-> *s*
        (modify-session {:user-id 543021 :double-authed? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (some-text? "New message")))))

(deftest browse-treatment
  (let [random-message (str (UUID/randomUUID))]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "in-treatment" :password "IN-treatment88"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Your treatment started"))
        (visit "/user/xxx")
        (has (status? 404))
        (visit "/user/tx/messages")
        (has (some-text? "New message"))
        (visit "/user/tx/messages" :request-method :post :params {:text random-message})
        (has (status? 302))
        (visit "/user/tx/messages")
        (has (some-text? random-message))
        (visit "/user/tx/modules")
        (has (some-text? "Read module text"))
        (visit "/user/tx/module/5787")
        (has (status? 200))
        (visit "/user/tx/module/5787/homework")
        (has (status? 404))
        (visit "/user/tx/module/3973")
        (has (status? 404))
        (visit "/user/tx/module/3961")
        (has (status? 200))
        (visit "/user/tx/module/3961/homework")
        (has (status? 200))
        (visit "/user/tx/module/3961/homework" :request-method :post :params {:submit? 1 :content-data (json/write-str {})})
        (visit "/user/tx/module/3961/homework")
        (has (some-text? "Retract"))
        (visit "/user/tx/module/3961/retract-homework" :request-method :post)
        (visit "/user/tx/module/3961/homework")
        (not-text? "Retract")
        (visit "/user/tx/module/3961/worksheet/4001")
        (has (status? 200))
        (visit "/user/tx/module/3961/worksheet/4000")
        (has (status? 404))
        (visit "/user/tx/module/3974/worksheet/4000")
        (has (status? 200))
        (visit "/user/tx/error-report")
        (has (some-text? "Problems with website"))
        (visit "/user/tx/error-report" :request-method :post :params {:hello "xxx"})
        (has (status? 400))
        (visit "/user/tx/error-report" :request-method :post :params {:error-description (apply str (repeat (* 2 error-report-response/max-chars) "x"))})
        (has (status? 400))
        (visit "/user/tx/error-report" :request-method :post :params {:error-description (apply str (repeat error-report-response/max-chars "x"))})
        (has (status? 200)))))

(deftest ns-imports-exports-write-exports
  (with-redefs [privacy-service/user-must-consent? (constantly false)])
  (let [user-id             (user-service/create-user! 543018 {:firstname "import-export"})
        treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                :parent-id     user-id
                                                                :property-name "TreatmentAccesses"}))]
    (db/update-object-properties! {:table-name "c_treatmentaccess"
                                   :object-id  treatment-access-id
                                   :updates    {:AccessEnabled true}})
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     642517
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (log-headers)
        (has (some-text? "Start page"))
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/user/tx/module/642529/worksheet/642519" :request-method :post :params {:content-data (json/write-str {"export-content-ws$export" "1"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642520" :request-method :post :params {:content-data (json/write-str {"export-module-ws$export" "2"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642521" :request-method :post :params {:content-data (json/write-str {"export-alias-ws$export" "3"})})
        (has (status? 302))
        (visit "/user/tx/module/642518/worksheet/642528")
        (has (some-text? "\"export-content-ws\":{\"export\":\"1\"}"))
        (has (some-text? "\"export-module-ws\":{\"export\":\"2\"}"))
        (has (some-text? "\"alias\":{\"export\":\"3\"}"))
        (visit "/user/tx/module/642529/worksheet/642522" :request-method :post :params {:content-data (json/write-str {"export-content-main$export" "4"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642523" :request-method :post :params {:content-data (json/write-str {"export-module-main$export" "5"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642524" :request-method :post :params {:content-data (json/write-str {"export-alias-main$export" "6"})})
        (has (status? 302))
        (visit "/user/tx/module/642518/")
        (has (some-text? "\"export-content-main\":{\"export\":\"4\"}"))
        (has (some-text? "\"export-module-main\":{\"export\":\"5\"}"))
        (has (some-text? "\"alias\":{\"export\":\"6\"}"))
        (visit "/user/tx/module/642529/worksheet/642525" :request-method :post :params {:content-data (json/write-str {"export-content-hw$export" "7"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642526" :request-method :post :params {:content-data (json/write-str {"export-module-hw$export" "8"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642527" :request-method :post :params {:content-data (json/write-str {"export-alias-hw$export" "9"})})
        (has (status? 302))
        (visit "/user/tx/module/642518/homework")
        (has (some-text? "\"export-content-hw\":{\"export\":\"7\"}"))
        (has (some-text? "\"export-module-hw\":{\"export\":\"8\"}"))
        (has (some-text? "\"alias\":{\"export\":\"9\"}"))
        (visit "/user/tx/module/642529/worksheet/642534" :request-method :post :params {:content-data (json/write-str {"override$export" "0"})})
        (has (status? 302))
        (visit "/user/tx/module/642518/worksheet/642535")
        (has (some-text? "{\"override\":{\"export\":\"0\"}")))))

(deftest ns-imports-exports-write-imports
  (with-redefs [privacy-service/user-must-consent? (constantly false)])
  (let [user-id             (user-service/create-user! 543018 {:firstname "import-export"})
        treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                :parent-id     user-id
                                                                :property-name "TreatmentAccesses"}))]
    (db/update-object-properties! {:table-name "c_treatmentaccess"
                                   :object-id  treatment-access-id
                                   :updates    {:AccessEnabled true}})
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     642517
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (log-headers)
        (has (some-text? "Start page"))
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/user/tx/module/642518/worksheet/642528" :request-method :post :params {:content-data (json/write-str {"export-content-ws$export" "1"
                                                                                                                       "export-module-ws$export"  "2"
                                                                                                                       "alias$export"             "3"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642519")
        (has (some-text? "\"export-content-ws\":{\"export\":\"1\"}"))
        (visit "/user/tx/module/642529/worksheet/642520")
        (has (some-text? "\"export-module-ws\":{\"export\":\"2\"}"))
        (visit "/user/tx/module/642529/worksheet/642521")
        (has (some-text? "\"export-alias-ws\":{\"export\":\"3\"}"))
        (visit "/user/tx/module/642518/" :request-method :post :params {:content-data (json/write-str {"export-content-main$export" "4"
                                                                                                       "export-module-main$export"  "5"
                                                                                                       "alias$export"               "6"})})
        (has (status? 200))
        (visit "/user/tx/module/642529/worksheet/642522")
        (has (some-text? "\"export-content-main\":{\"export\":\"4\"}"))
        (visit "/user/tx/module/642529/worksheet/642523")
        (has (some-text? "\"export-module-main\":{\"export\":\"5\"}"))
        (visit "/user/tx/module/642529/worksheet/642524")
        (has (some-text? "\"export-alias-main\":{\"export\":\"6\"}"))

        (visit "/user/tx/module/642518/homework" :request-method :post :params {:content-data (json/write-str {"export-content-hw$export" "7"
                                                                                                               "export-module-hw$export"  "8"
                                                                                                               "alias$export"             "9"})
                                                                                :submit?      false})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642525")
        (has (some-text? "\"export-content-hw\":{\"export\":\"7\"}"))
        (visit "/user/tx/module/642529/worksheet/642526")
        (has (some-text? "\"export-module-hw\":{\"export\":\"8\"}"))
        (visit "/user/tx/module/642529/worksheet/642527")
        (has (some-text? "\"export-alias-hw\":{\"export\":\"9\"}"))

        (visit "/user/tx/module/642518/worksheet/642535" :request-method :post :params {:content-data (json/write-str {"override$export" "0"})})
        (has (status? 302))
        (visit "/user/tx/module/642529/worksheet/642534")
        (has (some-text? "{\"override\":{\"export\":\"0\"}")))))