(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [bass4.utils :as utils]
            [bass4.session.timeout :as session-timeout]
            [bass4.http-utils :as h-utils]
            [bass4.php-interop :as php-interop]
            [clojure.core.cache :as cache]
            [clojure.tools.logging :as log]))

;; Save path for uids so that user is redirected if uid is reused.
;; Old UIDs are valid for 24 hours
(defonce old-uids (atom (cache/ttl-cache-factory {} :ttl (* 24 1000 60 60))))

(defn create-embedded-session
  [_ request uid]
  (let [{:keys [user-id path php-session-id authorizations redirect]} (php-interop/data-for-uid! uid)]
    (when-not (or (nil? authorizations) (set? authorizations))
      (throw (Exception. "Authorizations must be a set, if set.")))
    (if (every? identity [user-id path php-session-id])
      (let [session             (:session request)
            same-session?       (and (= (:user-id session) user-id)
                                     (= (:php-session-id session) php-session-id))
            prev-embedded-paths (if (and same-session? (::embedded-paths session))
                                  (::embedded-paths session)
                                  #{})
            prev-authorizations (if (and same-session? (::authorizations session))
                                  (::embedded-paths session)
                                  #{})
            redirect            (str "/embedded/" (or redirect path))]
        (swap! old-uids assoc uid redirect)
        (-> (http-response/found redirect)
            (assoc :session {:user-id         user-id
                             ::embedded-paths (conj prev-embedded-paths path)
                             :php-session-id  php-session-id
                             ::authorizations (into prev-authorizations authorizations)
                             :external-login? true})))
      (if (contains? @old-uids uid)
        (http-response/found (get @old-uids uid))
        (h-utils/text-response "Wrong uid.")))))

(defn legal-character
  [c]
  (some #(= c %) ["=" "/" "?" "&"]))

(defn matches-embedded?
  [current embedded]
  (let [current-length  (count current)
        embedded-length (count embedded)]
    (and
      current
      embedded
      (string/starts-with? current embedded)
      (if (> current-length embedded-length)
        (or (legal-character (subs current (dec embedded-length) embedded-length))
            (legal-character (subs current embedded-length (inc embedded-length))))
        true))))

(defn embedded-request
  [handler request]
  (let [current-path   (:uri request)
        session        (:session request)
        embedded-paths (::embedded-paths session)
        php-session-id (:php-session-id session)
        timeouts       (php-interop/get-staff-timeouts)]
    (if (string/starts-with? current-path "/embedded/error/")
      (handler request)
      (if (and embedded-paths (some #(matches-embedded? current-path (str "/embedded/" %)) embedded-paths))
        (case (php-interop/check-php-session timeouts (:session request))
          (::php-interop/user-mismatch ::php-interop/no-session ::php-interop/absolute-timeout)
          (->
            (http-response/found "/embedded/error/no-session")
            (assoc :session {}))

          ::php-interop/re-auth-timeout
          (http-response/found "/embedded/error/re-auth")

          ::php-interop/ok
          (binding [session-timeout/*timeout-hard-override* (:absolute-timeout timeouts)]
            (php-interop/update-php-session-last-activity! php-session-id (utils/current-time))
            (handler request)))
        (http-response/forbidden "No embedded access")))))

(def ^:dynamic *embedded-request?* false)

(defn handle-embedded
  [handler request]
  (binding [*embedded-request?* true]
    (let [path (:uri request)
          uid  (get-in request [:params :uid])]
      (if (string/starts-with? path "/embedded/create-session")
        (create-embedded-session handler request uid)
        (embedded-request handler request)))))

(defn wrap-embedded-request
  [handler request]
  (if (when-let [path (:uri request)]
        (string/starts-with? path "/embedded"))
    (handle-embedded handler request)
    (handler request)))


(defn embedded-iframe
  [handler request]
  (let [response (handler request)]
    (if-let [path (:uri request)]
      (if (string/starts-with? path "/embedded/iframe")
        (update-in response [:headers] #(-> %
                                            (dissoc "X-Frame-Options")
                                            (assoc "X-XSS-Protection" "0")))
        response)
      response)))