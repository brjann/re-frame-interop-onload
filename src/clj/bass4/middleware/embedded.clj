(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [bass4.utils :as utils]
            [bass4.session.timeout :as session-timeout]
            [bass4.http-utils :as h-utils]
            [bass4.php-interop :as php-interop]))

(defn get-session-file
  [uid]
  (let [info (or (php-interop/data-for-uid! uid)
                 (php-interop/read-session-file uid))]
    (assoc info :user-id (utils/str->int (:user-id info)))))

(defn create-embedded-session
  [_ request uid]
  (let [{:keys [user-id path php-session-id]} (get-session-file uid)]
    (if (every? identity [user-id path php-session-id])
      (let [session             (:session request)
            prev-embedded-paths (if (and (:embedded-paths session)
                                         (= (:user-id session) user-id)
                                         (= (:php-session-id session) php-session-id))
                                  (:embedded-paths session)
                                  #{})
            embedded-paths      (conj prev-embedded-paths path)]
        (-> (http-response/found path)
            (assoc :session {:user-id         user-id
                             :embedded-paths  embedded-paths
                             :php-session-id  php-session-id
                             :external-login? true})))
      (h-utils/text-response "Wrong uid."))))

(defn legal-character
  [c]
  (some #(= c %) ["=" "/" "?" "&"]))

(defn matches-embedded
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

(defn check-embedded-path
  [handler request]
  (let [current-path   (:uri request)
        session        (:session request)
        embedded-paths (:embedded-paths session)
        php-session-id (:php-session-id session)
        timeouts       (php-interop/get-staff-timeouts)]
    (if (string/starts-with? current-path "/embedded/error/")
      (handler request)
      (if (and embedded-paths (some #(matches-embedded current-path (str "/embedded/" %)) embedded-paths))
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

(defn embedded-request [handler request uid]
  (let [embedded-paths  (get-in request [:session :embedded-paths] #{})
        url-uid-session (when uid
                          (let [{:keys [user-id path php-session-id]}
                                (get-session-file uid)]
                            {:user-id         user-id
                             :embedded-paths  (conj embedded-paths path)
                             :php-session-id  php-session-id
                             :external-login? true}))]
    (check-embedded-path handler (update request :session #(merge % url-uid-session)))))

(def ^:dynamic *embedded-request?* false)

(defn handle-embedded
  [handler request]
  (binding [*embedded-request?* true]
    (let [path (:uri request)
          uid  (get-in request [:params :uid])]
      (if (string/starts-with? path "/embedded/create-session")
        (create-embedded-session handler request uid)
        (embedded-request handler request uid)))))

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