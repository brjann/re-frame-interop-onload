(ns bass4.services.auth
  (:require [bass4.db.core :as db]
            [ring.util.http-response :as response]
    #_[buddy.hashers :as hashers]
            [clojure.tools.logging :as log]
            [bass4.services.user :as user]
            [clj-time.core :as t]))

#_(defn authenticate [id password]
    (when-let [user (db/get-user {:id id})]
      (when (hashers/check password (:password user))
        id)))

(def password-chars [2 3 4 6 7 8 9 "a" "b" "d" "e" "g" "h" "p" "r" "A" "B" "C" "D" "E" "F" "G" "H" "J" "K" "L" "M" "N" "P" "Q" "R" "T" "W" "X" "Y" "Z"])

(defn- double-auth-code []
  (clojure.string/join
    ""
    (map
      #(get password-chars %1)
      (repeatedly 3 #(rand-int (- (count password-chars) 1))))))

(defn double-auth-required? []
  true)

(defn double-authed? [session]
  (if (double-auth-required?)
    (boolean (:double-authed session))
    false))

(defn authenticate-by-user-id [user-id password]
  (when-let [user (db/get-user-by-user-id {:user-id user-id})]
    (when (= (:password user) password)
      (:objectid user))))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (when (= (:password user) password)
      (:objectid user))))

(defn- new-session-map [id add-double-auth]
  (merge
    {:identity          id
     :auth-timeout      nil
     :last-request-time (t/now)}
    (when add-double-auth
      {:double-authed    nil
       :double-auth-code (double-auth-code)})))

;; TODO: response/found should not be here.
(defn login! [{:keys [session]} {:keys [username password]}]
  (if-let [id (authenticate-by-username username password)]
    (if (double-auth-required?)
      (-> (response/found "/double-auth")
          (assoc :session
                 (merge session
                   (new-session-map id true))))
      (-> (response/found "/user/messages")
          (assoc :session (merge session (new-session-map id false)))))
    (response/unauthorized {:result :unauthorized
                            :message "login failure"})))

(defn logout! []
  (-> (response/found "/login")
      (assoc :session nil)))


;; -------------
;;  DOUBLE AUTH
;; -------------


(defn not-authenticated? [session]
  (or (nil? (:identity session))
      (nil? (user/get-user (:identity session)))))

(defn not-double-auth-ok? [session]
  (or (not (double-auth-required?))
      (nil? (:double-auth-code session))))

(defn double-auth-done? [session]
  (:double-authed session))


;; -------------
;;  AUTH TIMEOUT
;; -------------


