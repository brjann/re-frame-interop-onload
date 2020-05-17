(ns bass4.test.core
  (:require [mount.core :as mount]
            [clojure.core.async :refer [alts!! timeout]]
            [bass4.db.core]
            [bass4.db.sql-wrapper]
            [bass4.utils :as utils]
            [kerodon.core :refer :all]
            [kerodon.test]
            [bass4.handler :refer :all]
            [clj-time.coerce :as tc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [bass4.middleware.core :as mw]
            [bass4.services.attack-detector :as a-d]
            [bass4.test.assessment-utils :refer :all]
            [clj-time.core :as t]
            [bass4.db-common :as db-common]
            [bass4.middleware.debug :as mw-debug]
            [bass4.config :as config]
            [bass4.now :as now]
            [bass4.i18n :as i18n]
            [clojure.string :as str]
            [bass4.external-messages.email-sender :as email]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.instrument.validation :as i-validation]
            [clojure.data.json :as json]
            [bass4.db.core :as db]
            [net.cgrand.enlive-html :as enlive]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.clients.core :as clients]
            [bass4.services.user :as user-service]
            [bass4.db.orm-classes :as orm]
            [bass4.db.sql-wrapper :as sql-wrapper]
            [clojure.java.jdbc :as jdbc]))

(def project-double-auth 536972)
(def project-double-auth-assessment-series 536974)
(def project-no-double-auth 536818)
(def project-no-double-auth-ass-series 536820)
(def project-reg-allowed 564610)
(def project-reg-allowed-ass-series 564612)
(def project-tx 543018)
(def tx-autoaccess 551356)
(def tx-ns-tests 642517)
(def tx-timelimited 3958)

(def s (atom nil))
(def ^:dynamic *s* nil)

(def ^:dynamic test-now (atom nil))
(def ^:dynamic test-current-time (atom nil))

(defn modify-session
  [s session]
  (binding [mw-debug/*session-modification* session]
    (visit s "/debug/nothing")))

(defn link-user-to-treatment!
  [user-id treatment-id access-properties]
  (let [treatment-access-id (:objectid (orm/create-bass-object-map! {:class-name    "cTreatmentAccess"
                                                                     :parent-id     user-id
                                                                     :property-name "TreatmentAccesses"}))]
    (orm/update-object-properties! "c_treatmentaccess"
                                   treatment-access-id
                                   (merge {"AccessEnabled" true}
                                          access-properties))
    (db/create-bass-link! {:linker-id     treatment-access-id
                           :linkee-id     treatment-id
                           :link-property "Treatment"
                           :linker-class  "cTreatmentAccess"
                           :linkee-class  "cTreatment"})))

(defn create-user-with-treatment!
  ([treatment-id]
   (create-user-with-treatment! treatment-id false {}))
  ([treatment-id with-login?]
   (create-user-with-treatment! treatment-id with-login? {}))
  ([treatment-id with-login? access-properties]
   (let [user-id (user-service/create-user! project-tx)]
     (when with-login?
       (user-service/update-user-properties! user-id {:username user-id
                                                      :password user-id}))
     (link-user-to-treatment! user-id treatment-id access-properties)
     user-id)))

(defn create-user-with-password!
  ([] (create-user-with-password! {}))
  ([properties]
   (let [user-id (user-service/create-user! project-double-auth)]
     (user-service/update-user-properties!
       user-id
       (merge {:username user-id
               :password user-id}
              properties))
     user-id)))

(defn create-assessment-group!
  ([project assessment-series] (create-assessment-group! project assessment-series [286]))
  ([project assessment-series instruments]
   (create-assessment-group! project
                             assessment-series
                             instruments
                             {"WelcomeText"  "Welcome"
                              "ThankYouText" "Thanks"}))
  ([project assessment-series instruments texts]
   (let [reg-assessment (create-assessment! assessment-series
                                            (merge {"Scope" 1}
                                                   texts))
         reg-group      (create-group! project)]
     (doseq [instrument-id instruments]
       (link-instrument! reg-assessment instrument-id))     ; AAQ
     (create-group-administration! reg-group reg-assessment 1 {:date (midnight (now/now))})
     reg-group)))

(defmacro fix-time
  [body]
  `(do
     #_(reset! test-current-time (utils/current-time))
     #_(reset! test-now (now/now))
     (binding [test-current-time  (atom (utils/current-time))
               test-now           (atom (now/now))
               now/now            (fn [] @test-now)
               utils/current-time (fn [] @test-current-time)]
       ~body)))

(defn advance-time-s!
  ([secs]
   (swap! test-now #(t/plus % (t/seconds secs)))
   (swap! test-current-time #(+ % secs)))
  ([state secs]
   (advance-time-s! secs)
   state))

(defn advance-time-h!
  ([hours]
   (swap! test-now #(t/plus % (t/hours hours)))
   (swap! test-current-time (fn [ct]
                              (let [ct-time  (tc/from-epoch ct)
                                    new-time (t/plus ct-time (t/hours hours))]
                                (tc/to-epoch new-time)))))
  ([state hours]
   (advance-time-s! hours)
   state))

(defn advance-time-d!
  ([days]
   (swap! test-now #(t/plus % (t/days days)))
   (swap! test-current-time (fn [ct]
                              (let [ct-time  (tc/from-epoch ct)
                                    new-time (t/plus ct-time (t/days days))]
                                (tc/to-epoch new-time)))))
  ([state days]
   (advance-time-d! days)
   state))

#_(defn on-start
    []
    (log/error "ON-START")
    (log/error config/env)
    (let [x (mount/start
              #'config/env
              #'db-common/common-config
              ;#'bass4.db.core/metrics-reg
              #'db/db-common
              #'clients/client-configs
              #'clients/client-db-connections
              #'i18n/i18n-map)])
    (log/error (mount/running-states))
    (log/error "CURRENT-STATE")
    #_(log/error (mount/current-state #'config/env))
    #_(Thread/sleep 1000)
    (log/error @bass4.clients.core/db-connections*)
    #_(let [test-db (config/env :test-db)
            db      @(get clients/client-db-connections test-db)]
        #_(jdbc/execute! db ["TRUNCATE c_participantadministration"])))

(def first-run (atom true))
(defn test-fixtures
  [f]
  (mount/start
    #'bass4.config/env
    #'db-common/common-config
    ;#'bass4.db.core/metrics-reg
    #'db/db-common
    #'clients/client-configs
    #'clients/client-db-connections
    #'i18n/i18n-map)
  (when (nil? @s)
    (swap! s (constantly (session (app)))))
  (let [test-db (config/env :test-db)]
    (binding [sql-wrapper/*email-deadlock*     false
              user-service/*log-user-changes*  false
              email/*email-reroute*            :void
              sms/*sms-reroute*                :void
              clojure.test/*stack-trace-depth* 10
              mw/*skip-csrf*                   true
              config/test-mode?                true
              *s*                              @s
              i-validation/*validate-answers?  false
              request-logger/*request-host*    (config/env :test-host)
              clients/*client-config*          (get clients/client-configs test-db)
              db/*db*                          @(get clients/client-db-connections test-db)]
      (when @first-run
        (reset! first-run false)
        (jdbc/execute! db/*db* ["TRUNCATE c_participant"])
        (jdbc/execute! db/*db* ["TRUNCATE c_participantadministration"])
        (jdbc/execute! db/*db* ["TRUNCATE c_group"])
        (jdbc/execute! db/*db* ["TRUNCATE c_groupadministration"])
        (jdbc/execute! db/*db* ["TRUNCATE c_assessment"])
        (jdbc/execute! db/*db* ["TRUNCATE c_instrumentanswers"])
        (jdbc/execute! db/*db* ["TRUNCATE c_treatmentaccess"])
        (jdbc/execute! db/*db* ["TRUNCATE c_flag"]))
      (f))))

(defn disable-attack-detector [f]
  (binding [a-d/get-delay-time         (constantly nil)
            a-d/register-failed-login! (constantly nil)]
    (f)))

(defn debug-headers-not-text?
  [response & strs]
  (let [headers (or (get-in response [:response :headers "X-Debug-Headers"]) "")]
    (clojure.test/do-report {:actual   headers
                             :type     (if (some #(.contains headers %) strs)
                                         :fail
                                         :pass)
                             :message  ""
                             :expected (str "Not any of " (str/join ", " strs))}))
  response)

(defn debug-headers-text?
  [response & strs]
  (let [headers (or (get-in response [:response :headers "X-Debug-Headers"]) "")]
    (clojure.test/do-report {:actual   headers
                             :type     (if (every? #(.contains headers %) strs)
                                         :pass
                                         :fail)
                             :message  ""
                             :expected (str/join ", " strs)}))
  response)

(defn fn-not-text?
  [response text]
  (let [body (get-in response [:response :body])]
    (clojure.test/do-report {:actual   (str "has " text)
                             :type     (if (.contains body text)
                                         :fail
                                         :pass)
                             :message  ""
                             :expected (str "does not have " text)}))
  response)

(defn poll-message-chan
  ([c] (poll-message-chan c 1))
  ([c n]
   (for [i (range n)]
     (let [[res _] (alts!! [c (timeout 1000)])]
       (when (nil? res)
         (throw (Exception. (str "Channel " i " timed out"))))
       (-> res
           :message
           (select-keys [:type :message]))))))

(defn any-match?
  [msg-pred messages]
  (let [match? (fn [msg-pred message]
                 (when (and (= (first msg-pred) (:type message))
                            (str/includes? (:message message) (second msg-pred)))
                   msg-pred))]
    (some #(match? msg-pred %1) messages)))

(defmacro messages-are?
  [msg-preds messages]
  `(let [msg-preds# ~msg-preds
         messages#  ~messages
         res#       (into #{} (map #(any-match? %1 messages#) msg-preds#))]
     (doseq [msg-pred# msg-preds#]
       (clojure.test/do-report {:actual   messages#
                                :type     (if (contains? res# msg-pred#)
                                            :pass
                                            :fail)
                                :message  "Message not found"
                                :expected msg-pred#}))))

(defmacro json-sub-map?
  [expected]
  `(fn [response# msg#]
     (let [body#         (get-in response# [:response :body])
           response-map# (json/read-str body#)
           sub-map#      (select-keys response-map# (keys ~expected))]
       (is (= ~expected sub-map#) msg#))
     response#))

(defn sub-map?
  [sub-map m]
  (let [sub-map* (select-keys m (keys sub-map))]
    (= sub-map* sub-map)))

(defn api-response
  [s]
  (let [str (-> s
                :enlive
                (enlive/texts)
                (first))]
    (when str
      (json/read-str str :key-fn keyword))))

(defmacro api-response?
  ([expected]
   `(kerodon.test/validate =
                           api-response
                           ~expected
                           (~'api-response? ~expected)))
  ([transform expected]
   `(kerodon.test/validate =
                           (comp ~transform api-response)
                           ~expected
                           (~'api-response? ~expected))))

(defn- collapse-extra-whitespace
  "Replace one or more consecutive whitespaces with a single space,
  i.e., unwraps all text onto a single line."
  [state]
  (-> (:enlive state)
      (enlive/texts)
      (str/join)
      (str/replace #"\s+" " ")))

(defmacro not-text?
  [expected]
  `(kerodon.test/validate #(not (.contains ^String %1 ^CharSequence %2))
                          ~collapse-extra-whitespace
                          ~expected
                          (~test "not=" ~expected)))

(defmacro pass-by
  [prev form]
  `((fn [] (let [x# ~prev]
             ~form
             x#))))

(defn log-return
  ([x]
   (log/debug x)
   x)
  ([x y]
   (log/debug y)
   x))

(defn log-status
  ([x]
   (log/debug (get-in x [:response :status]))
   x))

(defn log-response
  ([x]
   (log/debug (:response x))
   x))

(defn log-headers
  ([x]
   (log/debug (get-in x [:response :headers]))
   x))

(defn log-body
  ([x]
   (log/debug (get-in x [:response :body]))
   x))

(defn log-session
  ([s]
   (log-body (visit s "/debug/session"))
   s))

(defn log-api-response
  ([s]
   (log/debug (api-response s))
   s))

(defmacro ->! [a & forms]
  `(if-not (and (symbol? '~a)
                (= (class ~a) clojure.lang.Atom))
     (throw (Exception. "Arg a must be an atom and symbol"))
     (let [new-a# (-> @~a ~@forms)]
       (reset! ~a new-a#))))



;; Exploring creating a new database for every test run
#_(let [db-name (str (UUID/randomUUID))]
    (shell/sh "/Applications/MAMP/Library/bin/mysql" "-uroot" "-proot" :in (str "create database `" db-name "`;"))
    (shell/sh "/Applications/MAMP/Library/bin/mysql" "-uroot" "-proot" db-name :in (clojure.java.io/file "/Users/brjljo/Box Sync/BASS Platform/databases/TEST-structure-dump-reduced.sql"))
    (jdbc/with-db-connection [conn {:dbtype "mysql" :port 3300 :dbname db-name :user "root" :password "root" "serverTimezone" "UTC"}]
      (binding [db/*db* conn]
        (orm/create-bass-object-map! {:class-name "cParticipant" :property-name "XXX" :parent-id 666}))))