(ns bass4.test.reqs-api
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     log-return
                                     log-body
                                     log-status
                                     log-headers
                                     log-session
                                     disable-attack-detector
                                     *s*
                                     modify-session
                                     poll-message-chan
                                     messages-are?
                                     api-response?
                                     api-response
                                     ->!
                                     log-api-response
                                     pass-by]]
            [clojure.tools.logging :as log]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.services.user :as user-service]
            [bass4.time :as b-time]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-api-re-auth
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (modify-session {:last-request-time (t/date-time 1985 10 26 1 20 0 0)})
      (visit "/api/user/tx/messages")
      (has (status? 440))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (visit "/user/tx/messages")
      (has (status? 200))))


(defn create-user-with-treatment!
  ([treatment-id]
   (create-user-with-treatment! treatment-id false {}))
  ([treatment-id with-login?]
   (create-user-with-treatment! treatment-id with-login? {}))
  ([treatment-id with-login? access-properties]
   (let [user-id             (user-service/create-user! 543018 {:Group     "537404"
                                                                :firstname "tx-text"})
         treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                 :parent-id     user-id
                                                                 :property-name "TreatmentAccesses"}))]
     (when with-login?
       (user-service/update-user-properties! user-id {:username user-id
                                                      :password user-id}))
     (db/update-object-properties! {:table-name "c_treatmentaccess"
                                    :object-id  treatment-access-id
                                    :updates    (merge {:AccessEnabled true}
                                                       access-properties)})
     (db/create-bass-link! {:linker-id     treatment-access-id
                            :linkee-id     treatment-id
                            :link-property "Treatment"
                            :linker-class  "cTreatmentAccess"
                            :linkee-class  "cTreatment"})
     user-id)))


(deftest request-errors
  (let [user-id (create-user-with-treatment! 551356 false {:MessagesSendDisallow true})]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/re-auth" :request-method :post :body-params {:module-id 666})
        (has (status? 400))
        (visit "/api/user/tx/module-main/xxx")
        (has (status? 400))
        (visit "/api/user/tx/module-main/666")
        (has (status? 404))
        ;; Module 4009 not active
        (visit "/api/user/tx/module-main/4009")
        (has (status? 403))
        (visit "/api/user/tx/module-homework/4009")
        (has (status? 403))
        (visit "/api/user/tx/module-worksheet/4009/5119")
        (has (status? 403))
        (visit "/api/user/tx/module-homework/xxx")
        (has (status? 400))
        (visit "/api/user/tx/module-homework/666")
        (has (status? 404))
        ;; Module 5787 has no homework
        (visit "/api/user/tx/module-homework/5787")
        (has (status? 404))
        (visit "/api/user/tx/module-homework-submit" :request-method :put :body-params {:module-id "xx"})
        (has (status? 400))
        (visit "/api/user/tx/module-homework-submit" :request-method :put :body-params {:module-id 666})
        (has (status? 404))
        (visit "/api/user/tx/module-worksheet/xxx/yyy")
        (has (status? 400))
        (visit "/api/user/tx/module-worksheet/666/yyy")
        (has (status? 400))
        (visit "/api/user/tx/module-worksheet/666/666")
        (has (status? 404))
        (visit "/api/user/tx/module-worksheet/4003/666")
        (has (status? 404))
        ;; Worksheet 4019 not present in module 4003
        (visit "/api/user/tx/module-worksheet/4003/4019")
        (has (status? 404))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id "xx" :content-id "xxx"})
        (has (status? 400))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id 666 :content-id "xxx"})
        (has (status? 400))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id "xxx" :content-id 666})
        (has (status? 400))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id 666 :content-id 666})
        (has (status? 404))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id 4003 :content-id 666})
        (has (status? 404))
        (visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id "xx"})
        (has (status? 400))
        (visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id 666})
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/xxx/yyy")
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/666/yyy")
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/yyy/666")
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/666/666")
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/4003/666")
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/4003/4022")
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/xxx/yyy" :request-method :put :body-params {:data {}})
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/666/yyy" :request-method :put :body-params {:data {}})
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/xxx/666" :request-method :put :body-params {:data {}})
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/666/666" :request-method :put :body-params {:data {}})
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/4003/666" :request-method :put :body-params {:data {}})
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/4003/4022" :request-method :put :body-params {:data {}})
        (has (status? 404))
        (visit "/api/user/tx/module-content-data/4003/4001" :request-method :put :body-params {:xxx {}})
        (has (status? 400))
        (visit "/api/user/tx/module-content-data/4003/4001" :request-method :put :body-params {:data {"xxx" "yyy"}})
        (has (status? 400))
        (visit "/api/user/tx/content-data")
        (has (status? 400))
        (visit "/api/user/tx/content-data?xxx=yyy")
        (has (status? 400))
        (visit "/api/user/tx/content-data" :request-method :put :body-params {:xxx {}})
        (has (status? 400))
        (visit "/api/user/tx/content-data" :request-method :put :body-params {:data {"xxx" "yyy"}})
        (has (status? 400))
        (visit "/api/user/tx/message" :request-method :post :body-params {:message "xxx"})
        (has (status? 404)))))

(deftest iterate-treatment
  "Iterate all treatment components to ensure that responses
  fulfill schemas"
  (let [user-id (create-user-with-treatment! 551356)]
    (let [s           (-> *s*
                          (modify-session {:user-id user-id :double-authed? true})
                          (visit "/api/user/tx/treatment-info")
                          (has (status? 200))
                          (visit "/api/user/tx/modules")
                          (has (status? 200)))
          module-list (api-response s)]
      (doseq [module module-list]
        (let [module-id (:module-id module)]
          (when-not (:active? module)
            (->
              s
              (visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id module-id})
              (has (status? 200))))
          (when (:main-text module)
            (->
              s
              (visit (str "/api/user/tx/module-main/" module-id))
              (has (status? 200))))
          (doseq [worksheet (:worksheets module)]
            (->
              s
              (visit (str "/api/user/tx/module-worksheet/" module-id "/" (:content-id worksheet)))
              (has (status? 200))))
          (when (:homework module)
            (->
              s
              (visit (str "/api/user/tx/module-homework/" module-id))
              (has (status? 200)))))))))

(deftest submit-homework
  (let [user-id         (create-user-with-treatment! 551356)
        homework-status (fn [res]
                          (->> res
                               (filterv #(= 4002 (:module-id %)))
                               (first)
                               :homework-status))]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/modules")
        (has (status? 200))
        (has (api-response? homework-status nil))
        (visit "/api/user/tx/module-homework-submit" :request-method :put :body-params {:module-id 4002})
        (has (status? 200))
        (visit "/api/user/tx/modules")
        (has (api-response? homework-status "submitted")))))

(deftest module-content-accessed
  (let [user-id          (create-user-with-treatment! 551356)
        content-accessed (fn [res]
                           (->> res
                                (filter #(= 4002 (:module-id %)))
                                (first)
                                :homework
                                :accessed?))]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/modules")
        (has (api-response? content-accessed false))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id 4002 :content-id 4018})
        (has (status? 200))
        (visit "/api/user/tx/modules")
        (has (api-response? content-accessed true)))))

(deftest activate-module
  (let [user-id        (create-user-with-treatment! 551356 false)
        active-modules #(->> %
                             (filter :active?)
                             (map :module-id)
                             (into #{}))]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/modules")
        (has (api-response? active-modules #{5787 4002 4003 4007}))
        (visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id 3981})
        (visit "/api/user/tx/modules")
        (has (api-response? active-modules #{5787 4002 4003 4007 3981})))))

(defn- send-message-to-user
  [user-id message-text]
  (let [message-id (:objectid (db/create-bass-object! {:class-name    "cMessage"
                                                       :parent-id     user-id
                                                       :property-name "Messages"}))]
    (db/update-object-properties! {:table-name "c_message"
                                   :object-id  message-id
                                   :updates    {:MessageText message-text
                                                :ReadTime    0
                                                :Draft       0
                                                :SendTime    (b-time/to-unix (t/now))}})
    (db/create-bass-link! {:linker-id     message-id
                           :linkee-id     110
                           :link-property "Sender"
                           :linker-class  "cMessage"
                           :linkee-class  "cTherapist"})
    message-id))

(deftest send-message
  (let [user-id    (create-user-with-treatment! 551356)
        message-id (atom nil)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/messages")
        (has (api-response? []))
        (visit "/api/user/tx/message" :request-method :post :body-params {:message "xxx"})
        (has (status? 200))
        (visit "/api/user/tx/messages")
        (has (api-response? (comp #(select-keys % [:message :sender-type]) first) {:message "xxx" :sender-type "participant"}))
        (pass-by (reset! message-id (send-message-to-user user-id "zzz")))
        (visit "/api/user/tx/messages")
        (has (api-response? (comp #(select-keys % [:message :sender-type :unread?]) second)
                            {:message "zzz" :sender-type "therapist" :unread? true}))
        (visit "/api/user/tx/message-read" :request-method :put :body-params {:message-id @message-id})
        (visit "/api/user/tx/messages")
        (has (api-response? (comp #(select-keys % [:message :sender-type :unread?]) second)
                            {:message "zzz" :sender-type "therapist" :unread? false})))))

(deftest ns-imports-exports-write-exports
  (let [user-id (create-user-with-treatment! 642517)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (has (some-text? "Start page"))
        (visit "/api/user/tx/module-content-data/642529/642519" :request-method :put :body-params {:data {:export-content-ws {:export "1"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642520" :request-method :put :body-params {:data {:export-module-ws {:export "2"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642521" :request-method :put :body-params {:data {:export-alias-ws {:export "3"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642518/642528")
        (has (api-response? {:export-content-ws {:export "1"}
                             :export-module-ws  {:export "2"}
                             :alias             {:export "3"}}))
        (visit "/api/user/tx/module-content-data/642529/642522" :request-method :put :body-params {:data {:export-content-main {:export "4"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642523" :request-method :put :body-params {:data {:export-module-main {:export "5"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642524" :request-method :put :body-params {:data {:export-alias-main {:export "6"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642518/642532")
        (has (api-response? {:export-content-main {:export "4"}
                             :export-module-main  {:export "5"}
                             :alias               {:export "6"}}))
        (visit "/api/user/tx/module-content-data/642529/642525" :request-method :put :body-params {:data {:export-content-hw {:export "7"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642526" :request-method :put :body-params {:data {:export-module-hw {:export "8"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642527" :request-method :put :body-params {:data {:export-alias-hw {:export "9"}}})
        (visit "/api/user/tx/module-content-data/642518/642533")
        (has (api-response? {:export-content-hw {:export "7"}
                             :export-module-hw  {:export "8"}
                             :alias             {:export "9"}}))
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642534" :request-method :put :body-params {:data {:override {:export "0"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642518/642535")
        (has (api-response? {:override {:export "0"}})))))


(deftest ns-imports-exports-write-imports
  (let [user-id (create-user-with-treatment! 642517)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (has (some-text? "Start page"))
        (visit "/api/user/tx/module-content-data/642518/642528" :request-method :put :body-params {:data {:export-content-ws {:export "1"}
                                                                                                          :export-module-ws  {:export "2"}
                                                                                                          :alias             {:export "3"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642519")
        (has (api-response? {:export-content-ws {:export "1"}}))
        (visit "/api/user/tx/module-content-data/642529/642520")
        (has (api-response? {:export-module-ws {:export "2"}}))
        (visit "/api/user/tx/module-content-data/642529/642521")
        (has (api-response? {:export-alias-ws {:export "3"}}))
        (visit "/api/user/tx/module-content-data/642518/642532" :request-method :put :body-params {:data {:export-content-main {:export "4"}
                                                                                                          :export-module-main  {:export "5"}
                                                                                                          :alias               {:export "6"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642522")
        (has (api-response? {:export-content-main {:export "4"}}))
        (visit "/api/user/tx/module-content-data/642529/642523")
        (has (api-response? {:export-module-main {:export "5"}}))
        (visit "/api/user/tx/module-content-data/642529/642524")
        (has (api-response? {:export-alias-main {:export "6"}}))

        (visit "/api/user/tx/module-content-data/642518/642533" :request-method :put :body-params {:data {:export-content-hw {:export "7"}
                                                                                                          :export-module-hw  {:export "8"}
                                                                                                          :alias             {:export "9"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642525")
        (has (api-response? {:export-content-hw {:export "7"}}))
        (visit "/api/user/tx/module-content-data/642529/642526")
        (has (api-response? {:export-module-hw {:export "8"}}))
        (visit "/api/user/tx/module-content-data/642529/642527")
        (has (api-response? {:export-alias-hw {:export "9"}}))

        (visit "/api/user/tx/module-content-data/642518/642535" :request-method :put :body-params {:data {:override {:export "0"}}})
        (has (status? 200))
        (visit "/api/user/tx/module-content-data/642529/642534")
        (has (api-response? {:override {:export "0"}})))))