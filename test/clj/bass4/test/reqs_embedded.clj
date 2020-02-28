(ns ^:eftest/synchronized
  bass4.test.reqs-embedded
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.external-messages.async :refer [*debug-chan*]]
            [bass4.utils :as utils]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.php-interop :as php-interop]
            [bass4.services.user :as user-service]
            [bass4.module.services :as module-service]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (binding [*debug-chan* (chan)]
      (f))))

(defn get-php-session-id
  []
  (subs (str (UUID/randomUUID)) 0 32))

(deftest wrong-uid
  (with-redefs [php-interop/read-session-file (constantly {:user-id nil :path "instrument/1647" :php-session-id nil})]
    (-> *s*
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (has (some-text? "Wrong uid")))))

(deftest request-post-answers
  (let [php-session-id (get-php-session-id)
        now            (utils/to-unix (t/now))]
    (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id "UserId" 110 "LastActivity" now "SessionStart" now})
    (let [uid (php-interop/uid-for-data! {:user-id 110 :path "instrument/1647" :php-session-id php-session-id})]
      (-> *s*
          (visit (str "/embedded/create-session?uid=" uid))
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          (visit "/embedded/instrument/1647" :request-method :post :params {})
          (has (status? 400))
          (pass-by (messages-are? [[:email "nil error"]] (poll-message-chan *debug-chan*)))
          (visit "/embedded/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
          (pass-by (messages-are? [[:email "api/->json"]] (poll-message-chan *debug-chan*)))
          (has (status? 400))
          (visit "/embedded/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
          (has (status? 302))
          (visit "/embedded/instrument/535690")
          (has (status? 403))))))


(deftest request-wrong-instrument
  (let [uid (php-interop/uid-for-data! {:user-id 110 :path "instrument/" :php-session-id "xxx"})]
    (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
      (-> *s*
          (visit (str "/embedded/create-session?uid=" uid))
          (visit "/embedded/instrument/hell-is-here")
          (has (status? 400))
          (visit "/embedded/instrument/666")
          (has (status? 404))))))

(deftest request-not-embedded
  (-> *s*
      (visit "/embedded/instrument/hell-is-here")
      (has (status? 403))))

(deftest embedded-render
  (let [uid (php-interop/uid-for-data! {:user-id 110 :path "iframe/render" :php-session-id "xxx"})]
    (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
      (-> *s*
          (visit "/embedded/iframe/render")
          (has (status? 403))
          (visit (str "/embedded/create-session?uid=" uid))
          (visit "/embedded/iframe/render" :request-method :post :params {:text "Hejsan"})
          (has (status? 200))
          (has (some-text? "Hejsan"))))))

(deftest session-timeout
  (fix-time
    (let [php-session-id   (get-php-session-id)
          uid              (php-interop/uid-for-data! {:user-id 110 :path "instrument/1647" :php-session-id php-session-id})
          now              (utils/to-unix (t/now))
          timeouts         (php-interop/get-staff-timeouts)
          re-auth-timeout  (:re-auth-timeout timeouts)
          absolute-timeout (:absolute-timeout timeouts)]
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id "UserId" 110 "LastActivity" now "SessionStart" now})
      (-> *s*
          (visit (str "/embedded/create-session?uid=" uid))
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; Advance time to re-auth-timeout
          (advance-time-s! re-auth-timeout)
          (visit "/embedded/instrument/1647")
          (follow-redirect)
          (has (some-text? "Timeout"))
          ;; Fake re-auth
          (pass-by (php-interop/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; Advance time to re-auth-timeout in two steps
          (advance-time-s! (dec re-auth-timeout))
          (advance-time-s! 1)
          (visit "/embedded/instrument/1647")
          (has (status? 302))
          (pass-by (php-interop/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
          ;; Advance time to almost re-auth-timeout
          (advance-time-s! (dec re-auth-timeout))
          ;; Reload page (updating last activity)
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; Advance time to full re-auth-timeout
          (advance-time-s! 1)
          ;; Still logged in because of previous page reload
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; Advance time to absolute-timeout
          (advance-time-s! absolute-timeout)
          (visit "/embedded/instrument/1647")
          (follow-redirect)
          (has (some-text? "No session"))
          ;; Reload page (updating last activity)
          (pass-by (php-interop/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
          (visit "/embedded/instrument/1647")
          ;; Access error - session was destroyed
          (has (status? 403))))))

(deftest path-merge
  (fix-time
    (let [php-session-id-1 (get-php-session-id)
          php-session-id-2 (get-php-session-id)
          now              (utils/to-unix (t/now))
          uid1             (php-interop/uid-for-data! {:user-id 110 :path "instrument/1647" :php-session-id php-session-id-1})
          uid2             (php-interop/uid-for-data! {:user-id 110 :path "instrument/286" :php-session-id php-session-id-1})
          uid3             (php-interop/uid-for-data! {:user-id 110 :path "instrument/286" :php-session-id php-session-id-2})
          session-files    (atom [{:user-id 110 :path "instrument/286" :php-session-id php-session-id-2}
                                  {:user-id 110 :path "instrument/286" :php-session-id php-session-id-1}
                                  {:user-id 110 :path "instrument/1647" :php-session-id php-session-id-1}])]
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id-1 "UserId" 110 "LastActivity" now "SessionStart" now})
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id-2 "UserId" 110 "LastActivity" now "SessionStart" now})
      (-> *s*
          ;; First session file gives access to 1647
          (visit (str "/embedded/create-session?uid=" uid1))
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; No access to 286 yet
          (visit "/embedded/instrument/286")
          (has (status? 403))
          ;; First session file gives access to 286
          (visit (str "/embedded/create-session?uid=" uid2))
          (visit "/embedded/instrument/286")
          (has (status? 200))
          ;; Access to 1647 has been kept
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          ;; Third session file changes session id and gives access to 286
          (visit (str "/embedded/create-session?uid=" uid3))
          (visit "/embedded/instrument/286")
          (has (status? 200))
          ;; No access to 1647 because of changed session id
          (visit "/embedded/instrument/1647")
          (has (status? 403))))))

(deftest add-paths
  (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now)) :php-session-id "xx"})]
    (let [uid1 (php-interop/uid-for-data! {:user-id 110 :php-session-id "xx" :path "instrument/305"})
          uid  (php-interop/uid-for-data! {:user-id 110 :php-session-id "xx"})
          uid2 (php-interop/uid-for-data! {:user-id 110 :php-session-id "xx" :path "instrument/173"})]
      (php-interop/add-data-to-uid! uid {:path #{"instrument/1647"}})
      (php-interop/add-data-to-uid! uid {:path #{"instrument/286"}})
      (-> *s*
          (visit (str "/embedded/create-session?uid=" uid1))
          (has (status? 302))
          (follow-redirect)
          (has (status? 200))
          (visit "/embedded/instrument/305")
          (has (status? 200))
          (visit (str "/embedded/create-session?uid=" uid "&redirect=instrument/1647"))
          (has (status? 302))
          (follow-redirect)
          (has (status? 200))
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          (visit "/embedded/instrument/305")
          (has (status? 200))
          (visit "/embedded/instrument/286")
          (has (status? 200))
          (visit (str "/embedded/create-session?uid=" uid "&redirect=instrument/286"))
          (follow-redirect)
          (has (status? 200))
          (visit "/embedded/instrument/286")
          (has (status? 200))
          (visit "/embedded/instrument/173")
          (has (status? 403))
          (visit (str "/embedded/create-session?uid=" uid2))
          (follow-redirect)
          (has (status? 200))
          (visit "/embedded/instrument/173")
          (has (status? 200))
          (visit "/embedded/instrument/1647")
          (has (status? 200))))))

(defn create-user-with-treatment2!
  ([treatment-id]
   (create-user-with-treatment2! treatment-id false {}))
  ([treatment-id with-login?]
   (create-user-with-treatment2! treatment-id with-login? {}))
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
     [user-id treatment-access-id])))

(deftest embedded-api
  "Iterate all treatment components to ensure that responses
  fulfill schemas"
  (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
    (let [[user-id treatment-access-id] (create-user-with-treatment2! 551356)
          uid1    (php-interop/uid-for-data! {:user-id 110 :php-session-id "xxx" :path ""})
          uid2    (php-interop/uid-for-data! {:user-id        110
                                              :php-session-id "xxx"
                                              :path           ""
                                              :authorizations #{[:treatment/user-id user-id]}})
          api-url (fn [url] (str url "?user-id=" user-id "&treatment-access-id=" treatment-access-id))]
      (let [s           (-> *s*
                            (visit "/embedded/api/user-tx/modules")
                            (has (status? 403))
                            (visit (str "/embedded/create-session?uid=" uid1))
                            (visit "/embedded/api/user-tx/modules")
                            (has (status? 400))
                            (visit (api-url "/embedded/api/user-tx/modules"))
                            (has (status? 403))
                            (visit (str "/embedded/create-session?uid=" uid2))
                            (visit (api-url "/embedded/api/user-tx/modules"))
                            (has (status? 200)))
            module-list (api-response s)]
        (doseq [module module-list]
          (let [module-id (:module-id module)]
            (when-not (:active? module)
              (module-service/activate-module! treatment-access-id module-id))
            (when (:main-text module)
              (->
                s
                (visit (api-url (str "/embedded/api/user-tx/module-main/" module-id)))
                (has (status? 200))))
            (doseq [worksheet (:worksheets module)]
              (->
                s
                (visit (api-url (str "/embedded/api/user-tx/module-worksheet/" module-id "/" (:content-id worksheet))))
                (has (status? 200))))
            (when (:homework module)
              (->
                s
                (visit (api-url (str "/embedded/api/user-tx/module-homework/" module-id)))
                (has (status? 200))))))))))

(deftest embedded-api-ns
  (with-redefs [php-interop/get-php-session (constantly {:user-id 110 :last-activity (utils/to-unix (t/now))})]
    (let [[user-id treatment-access-id] (create-user-with-treatment2! 642517)
          api-url (fn [url] (str url "?user-id=" user-id "&treatment-access-id=" treatment-access-id))
          uid     (php-interop/uid-for-data! {:user-id        110
                                              :php-session-id "xxx"
                                              :path           ""
                                              :authorizations #{[:treatment/user-id user-id]}})]
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/api/user/tx/module-content-data/642518/642528" :request-method :put :body-params {:data {:export-content-ws {:export "1"}
                                                                                                            :export-module-ws  {:export "2"}
                                                                                                            :alias             {:export "3"}}})
          (has (status? 200)))
      (-> *s*
          (visit (api-url "/embedded/api/user-tx/module-content-data/642529/642519"))
          (has (status? 403))
          (visit (str "/embedded/create-session?uid=" uid))
          (visit (api-url "/embedded/api/user-tx/module-content-data/642529/642519"))
          (has (status? 200))
          (has (api-response? {:export-content-ws {:export "1"}}))
          (visit (api-url "/embedded/api/user-tx/module-content-data/642529/642520"))
          (has (api-response? {:export-module-ws {:export "2"}}))
          (visit (api-url "/embedded/api/user-tx/module-content-data/642529/642521"))
          (has (api-response? {:export-alias-ws {:export "3"}}))))))