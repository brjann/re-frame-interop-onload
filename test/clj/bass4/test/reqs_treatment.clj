(ns bass4.test.reqs-treatment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     disable-attack-detector
                                     *s*
                                     modify-session
                                     log-body
                                     log-headers
                                     log-response]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [clj-time.core :as t]
            [clojure.data.json :as json])
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
        (visit "/user/messages")
        (not-text? "New message")
        (visit "/user/messages" :request-method :post :params {:message "xxx"})
        (has (status? 404))
        (visit "/user/messages-save-draft" :request-method :post :params {:message "xxx"})
        (has (status? 404)))
    (-> *s*
        (modify-session {:user-id 543021 :double-authed? true})
        (visit "/user")
        (visit "/user/messages")
        (has (some-text? "New message")))))

(deftest browse-treatment
  (let [random-message (str (UUID/randomUUID))]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "in-treatment" :password "IN-treatment88"})
        (follow-redirect)
        (has (some-text? "Your treatment started"))
        (visit "/user/xxx")
        (has (status? 404))
        (visit "/user/messages")
        (has (some-text? "New message"))
        (visit "/user/messages" :request-method :post :params {:text random-message})
        (has (status? 302))
        (visit "/user/messages")
        (has (some-text? random-message))
        (visit "/user/modules")
        (has (some-text? "Read module text"))
        (visit "/user/module/5787")
        (has (status? 200))
        (visit "/user/module/5787/homework")
        (has (status? 404))
        (visit "/user/module/3973")
        (has (status? 404))
        (visit "/user/module/3961")
        (has (status? 200))
        (visit "/user/module/3961/homework")
        (has (status? 200))
        (visit "/user/module/3961/homework" :request-method :post :params {:submit? 1 :content-data (json/write-str {})})
        (visit "/user/module/3961/homework")
        (has (some-text? "Retract"))
        (visit "/user/module/3961/retract-homework" :request-method :post)
        (visit "/user/module/3961/homework")
        (not-text? "Retract")
        (visit "/user/module/3961/worksheet/4001")
        (has (status? 200))
        (visit "/user/module/3961/worksheet/4000")
        (has (status? 404))
        (visit "/user/module/3974/worksheet/4000")
        (has (status? 200)))))