(ns ^:eftest/synchronized
  bass4.test.reqs-api
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]
            [bass4.module.services :as module-service]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.config :as config])
  (:import (org.joda.time DateTime)))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-api-re-auth
  (let [user-id (create-user-with-treatment! tx-autoaccess true)]
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/api/user/tx/messages")
          (has (status? 440))
          (visit "/re-auth" :request-method :post :params {:password user-id})
          (visit "/user/tx/messages")
          (has (status? 200))))))

(deftest request-errors
  (let [user-id (create-user-with-treatment! tx-autoaccess false {:MessagesSendDisallow true})]
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
        #_(visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id "xx"})
        #_(has (status? 400))
        #_(visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id 666})
        #_(has (status? 404))
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

#_(deftest iterate-treatment
    "Iterate all treatment components to ensure that responses
    fulfill schemas"
    (let [user-id (create-user-with-treatment! 551356)]
      (let [s           (-> *s*
                            (modify-session {:user-id user-id :double-authed? true})
                            (visit "/api/user/tx/treatment-info")
                            (has (status? 200))
                            (visit "/api/user/timezone-name")
                            (has (status? 200))
                            (visit "/api/user/privacy-notice")
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

(deftest iterate-treatment-no-activate-module
  "Iterate all treatment components to ensure that responses
  fulfill schemas"
  (let [user-id             (create-user-with-treatment! tx-autoaccess)
        treatment-access-id (-> (treatment-builder/user-treatment user-id)
                                :treatment-access
                                :treatment-access-id)]
    (let [s           (-> *s*
                          (modify-session {:user-id user-id :double-authed? true})
                          (visit "/api/user/tx/treatment-info")
                          (has (status? 200))
                          (visit "/api/user/timezone-name")
                          (has (status? 200))
                          (visit "/api/user/privacy-notice")
                          (has (status? 200))
                          (visit "/api/user/tx/modules")
                          (has (status? 200)))
          module-list (api-response s)]
      (doseq [module module-list]
        (let [module-id (:module-id module)]
          (when-not (:active? module)
            (module-service/activate-module! treatment-access-id module-id))
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
              (has (status? 200))))))
      (-> s
          (visit "/api/logout" :request-method :post)
          (has (status? 200))
          (visit "/api/user/tx/modules")
          (has (status? 403))))))

(deftest submit-homework
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        homework-status (fn [module-id] (fn [res]
                                          (->> res
                                               (filterv #(= module-id (:module-id %)))
                                               (first)
                                               (#(if (nil? %)
                                                   (throw (Exception. (str "Module " module-id " does not exist")))
                                                   %))
                                               :homework-status)))]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/modules")
        (has (status? 200))
        (has (api-response? (homework-status 5787) nil))
        (has (api-response? (homework-status 4002) "not-submitted"))
        (visit "/api/user/tx/module-homework-submit" :request-method :put :body-params {:module-id 4002})
        (has (status? 200))
        (visit "/api/user/tx/modules")
        (has (api-response? (homework-status 4002) "submitted")))))

(deftest module-content-accessed
  (let [user-id          (create-user-with-treatment! tx-autoaccess)
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
        (visit (str "/api/user/tx/module-homework/" 4002))
        (has (api-response? :accessed? false))
        (visit "/api/user/tx/module-content-accessed" :request-method :put :body-params {:module-id 4002 :content-id 4018})
        (has (status? 200))
        (visit "/api/user/tx/modules")
        (has (api-response? content-accessed true))
        (visit (str "/api/user/tx/module-homework/" 4002))
        (has (api-response? :accessed? true)))))

(deftest module-content-last-updated
  (let [user-id           (create-user-with-treatment! tx-autoaccess)
        data-last-updated (fn [res]
                            (->> res
                                 (filter #(= 4002 (:module-id %)))
                                 (first)
                                 :homework
                                 :data-updated))]
    (api-response (-> *s*
                      (modify-session {:user-id user-id :double-authed? true})
                      (visit "/api/user/tx/modules")
                      (has (api-response? data-last-updated nil))
                      (visit "/api/user/tx/module-content-data/4002/4018" :request-method :put :body-params {:data {:hemuppgift2i {:xxx "1"}}})
                      (has (status? 200))
                      (visit "/api/user/tx/modules")
                      (has (api-response? (comp class
                                                tf/parse
                                                data-last-updated)
                                          DateTime))))))

#_(deftest activate-module
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
                                                :SendTime    (utils/to-unix (t/now))}})
    (db/create-bass-link! {:linker-id     message-id
                           :linkee-id     110
                           :link-property "Sender"
                           :linker-class  "cMessage"
                           :linkee-class  "cTherapist"})
    message-id))

(deftest send-message
  (let [user-id    (create-user-with-treatment! tx-autoaccess)
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
        (visit "/api/user/tx/treatment-info")
        (has (api-response? :new-message? true))
        (visit "/api/user/tx/messages")
        (has (api-response? (comp #(select-keys % [:message :sender-type :unread?]) second)
                            {:message "zzz" :sender-type "therapist" :unread? true}))
        (visit "/api/user/tx/message-read" :request-method :put :body-params {:message-id @message-id})
        (visit "/api/user/tx/messages")
        (has (api-response? (comp #(select-keys % [:message :sender-type :unread?]) second)
                            {:message "zzz" :sender-type "therapist" :unread? false}))
        (visit "/api/user/tx/treatment-info")
        (has (api-response? :new-message? false)))))

(deftest ns-write
  (let [user-id (create-user-with-treatment! tx-autoaccess)
        data    {:xxx {:www "1"
                       :zzz "2"}
                 :yyy {:www "3"
                       :zzz "4"}}]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/content-data?namespaces=xxx&namespaces=yyy")
        (has (status? 200))
        (has (api-response? nil))
        (visit "/api/user/tx/content-data" :request-method :put :body-params {:data data})
        (has (status? 200))
        (visit "/api/user/tx/content-data?namespaces=xxx&namespaces=yyy")
        (has (status? 200))
        (has (api-response? data)))))

(deftest module-content-tags
  (let [user-id (create-user-with-treatment! tx-ns-tests)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (has (some-text? "Start page"))
        (visit "/api/user/tx/treatment-info")
        (has (api-response? (comp (partial into #{}) :tags second :modules) #{"mtag1" "mtag2"}))
        (visit "/api/user/tx/modules")
        (has (api-response? (comp (partial into #{}) :tags second) #{"mtag1" "mtag2"}))
        (has (api-response? (comp (partial into #{}) :tags first :worksheets second) #{"ctag1" "ctag2"}))
        (visit "/api/user/tx/module-worksheet/642518/642528")
        (has (api-response? (comp (partial into #{}) :tags) #{"ctag1" "ctag2"}))
        (has (some-text? "ctag1"))
        (has (some-text? "ctag2")))))

(deftest ns-imports-exports-write-exports
  (let [user-id (create-user-with-treatment! tx-ns-tests)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (has (some-text? "Start page"))
        (visit "/api/user/tx/content-data-namespaces")
        (has (api-response? []))
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
        (has (api-response? {:override {:export "0"}}))
        (visit "/api/user/tx/content-data-namespaces")
        (has (api-response? #(into #{} %) #{"export-alias-hw"
                                            "export-alias-main"
                                            "export-alias-ws"
                                            "export-content-hw"
                                            "export-content-main"
                                            "export-content-ws"
                                            "export-module-hw"
                                            "export-module-main"
                                            "export-module-ws"
                                            "override"})))))


(deftest ns-imports-exports-write-imports
  (let [user-id (create-user-with-treatment! tx-ns-tests)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx")
        (has (some-text? "Start page"))
        (visit "/api/user/tx/content-data-namespaces")
        (has (api-response? []))
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
        (has (api-response? {:override {:export "0"}}))
        (visit "/api/user/tx/content-data-namespaces")
        (has (api-response? #(into #{} %) #{"export-alias-hw"
                                            "export-alias-main"
                                            "export-alias-ws"
                                            "export-content-hw"
                                            "export-content-main"
                                            "export-content-ws"
                                            "export-module-hw"
                                            "export-module-main"
                                            "export-module-ws"
                                            "override"})))))