(ns ^:eftest/synchronized
  bass4.test.reqs-treatment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.db.core :as db]
            [bass4.test.core :refer :all]
            [clj-time.core :as t]
            [clojure.data.json :as json]
            [bass4.responses.error-report :as error-report-response]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]
            [bass4.php-interop :as php-interop]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-messages
  (let [user-id1 (create-user-with-treatment! tx-autoaccess false {"MessagesSendDisallow" 1})
        user-id2 (create-user-with-treatment! tx-autoaccess false {"MessagesSendDisallow" 0})]
    (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
      (-> *s*
          (modify-session {:user-id user-id1 :double-authed? true})
          (visit "/user")
          (visit "/user/tx/messages")
          (fn-not-text? "New message")
          (visit "/user/tx/messages" :request-method :post :params {:message "xxx"})
          (has (status? 404))
          (visit "/user/tx/messages-save-draft" :request-method :post :params {:message "xxx"})
          (has (status? 404)))
      (-> *s*
          (modify-session {:user-id user-id2 :double-authed? true})
          (visit "/user")
          (visit "/user/tx/messages")
          (has (some-text? "New message"))))))

(deftest browse-treatment
  (let [random-message (str (UUID/randomUUID))
        user-id        (create-user-with-treatment! tx-timelimited true {"StartDate" (utils/to-unix (t/minus (t/now) (t/days 1)))
                                                                         "EndDate"   (utils/to-unix (t/plus (t/now) (t/days 1)))})]
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Welcome!"))
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
        (fn-not-text? "Retract")
        (visit "/user/tx/module/3961/worksheet/4001")
        (has (status? 200))
        (visit "/user/tx/module/3961/worksheet/4000")
        (has (status? 404))
        (visit "/user/tx/error-report")
        (has (some-text? "Problems with website"))
        (visit "/user/tx/error-report" :request-method :post :params {:hello "xxx"})
        (has (status? 400))
        (visit "/user/tx/error-report" :request-method :post :params {:error-description (apply str (repeat (* 2 error-report-response/max-chars) "x"))})
        (has (status? 400))
        (visit "/user/tx/error-report" :request-method :post :params {:error-description (apply str (repeat error-report-response/max-chars "x"))})
        (has (status? 200)))))

(deftest ns-imports-exports-write-exports
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
        (has (some-text? "{\"override\":{\"export\":\"0\"}")))
    (let [view-checks [[642518 642528 ["\"export-content-ws\":{\"export\":\"1\"}"
                                       "\"export-module-ws\":{\"export\":\"2\"}"
                                       "\"alias\":{\"export\":\"3\"}"]]
                       [642518 642532 ["\"export-content-main\":{\"export\":\"4\"}"
                                       "\"export-module-main\":{\"export\":\"5\"}"
                                       "\"alias\":{\"export\":\"6\"}"]]
                       [642518 642533 ["\"export-content-hw\":{\"export\":\"7\"}"
                                       "\"export-module-hw\":{\"export\":\"8\"}"
                                       "\"alias\":{\"export\":\"9\"}"]]]]
      (doseq [[module-id content-id texts] view-checks]
        (let [path (str "iframe/view-user-content/" treatment-access-id "/" module-id "/" content-id)
              uid  (php-interop/uid-for-data! {:user-id 110 :path path :php-session-id "xxx"})]
          (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
            (let [s (visit *s* (str "/embedded/create-session?uid=" uid))]
              (doseq [text texts]
                (-> s
                    (visit (str "/embedded/" path))
                    (has (some-text? text)))))))))))

(deftest ns-imports-exports-write-imports
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
                                                                                :submit?      0})
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
        (has (some-text? "{\"override\":{\"export\":\"0\"}")))
    (let [view-checks [[642529 642519 "\"export-content-ws\":{\"export\":\"1\"}"]
                       [642529 642520 "\"export-module-ws\":{\"export\":\"2\"}"]
                       [642529 642521 "\"export-alias-ws\":{\"export\":\"3\"}"]

                       [642529 642522 "\"export-content-main\":{\"export\":\"4\"}"]
                       [642529 642523 "\"export-module-main\":{\"export\":\"5\"}"]
                       [642529 642524 "\"export-alias-main\":{\"export\":\"6\"}"]

                       [642529 642525 "\"export-content-hw\":{\"export\":\"7\"}"]
                       [642529 642526 "\"export-module-hw\":{\"export\":\"8\"}"]
                       [642529 642527 "\"export-alias-hw\":{\"export\":\"9\"}"]]]
      (doseq [[module-id content-id text] view-checks]
        (let [path (str "iframe/view-user-content/" treatment-access-id "/" module-id "/" content-id)
              uid  (php-interop/uid-for-data! {:user-id 110 :path path :php-session-id "xxx"})]
          (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
            (-> *s*
                (visit (str "/embedded/create-session?uid=" uid))
                (visit (str "/embedded/" path))
                (has (some-text? text)))))))))