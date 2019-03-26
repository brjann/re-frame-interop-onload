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
                                     pass-by]]
            [clojure.tools.logging :as log]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.services.user :as user-service]
            [bass4.services.treatment :as treatment]
            [clojure.data.json :as json]))


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
  [treatment-id]
  (let [user-id             (user-service/create-user! 543018 {:Group "537404" :firstname "tx-text"})
        treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                :parent-id     user-id
                                                                :property-name "TreatmentAccesses"}))]
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     treatment-id
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})
    user-id))

(deftest request-errors
  (let [user-id (create-user-with-treatment! 551356)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/re-auth" :request-method :post :body-params {:module-id 666})
        (has (status? 400))
        (visit "/api/user/tx/module-main/xxx")
        (has (status? 400))
        (visit "/api/user/tx/module-main/666")
        (has (status? 404))
        (visit "/api/user/tx/module-homework/xxx")
        (has (status? 400))
        (visit "/api/user/tx/module-homework/666")
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
        (has (status? 400)))))

(defn api-response
  [s]
  (-> s
      :enlive
      first
      :content
      first
      :content
      first
      (json/read-str :key-fn keyword)))

(deftest module-list
  (let [user-id (create-user-with-treatment! 551356)]
    (let [res        (api-response (-> *s*
                                       (modify-session {:user-id user-id :double-authed? true})
                                       (visit "/api/user/tx/modules")))
          module-ids (->> res
                          (filter :active?)
                          (map :module-id)
                          (into #{}))]
      (is (= #{5787 4002 4003 4007} module-ids)))))

(deftest activate-module
  (let [user-id (create-user-with-treatment! 551356)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/api/user/tx/activate-module" :request-method :put :body-params {:module-id 3981}))
    (let [user-treatment (treatment/user-treatment user-id)]
      (is (= #{5787 4002 4003 4007 3981} (into #{} (map :module-id (filter :active? (get-in user-treatment [:tx-components :modules])))))))))