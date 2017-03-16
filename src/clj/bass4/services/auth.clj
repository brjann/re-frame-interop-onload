(ns bass4.services.auth
  (:require [bass4.db.core :as db]
            [ring.util.http-response :as response]
    #_[buddy.hashers :as hashers]
            [clojure.tools.logging :as log]
            [bass4.services.user :as user]))

#_(defn authenticate [id password]
    (when-let [user (db/get-user {:id id})]
      (when (hashers/check password (:password user))
        id)))

#_(defn authenticate [id password]
  (when-let [user (db/get-user {:id id})]
    (when (= (:password user) password)
      id)))

(def password-chars [2 3 4 6 7 8 9 "a" "b" "d" "e" "g" "h" "p" "r" "A" "B" "C" "D" "E" "F" "G" "H" "J" "K" "L" "M" "N" "P" "Q" "R" "T" "W" "X" "Y" "Z"])

(defn double-auth-required? []
  true)

(defn- double-auth-code []
  (clojure.string/join
    ""
    (map
      #(get password-chars %1)
      (repeatedly 3 #(rand-int (- (count password-chars) 1))))))

(defn double-authed? [session]
  (clojure.tools.logging/error session)
  (clojure.tools.logging/error (boolean (:double-authed session)))
  (if (double-auth-required?)
    (boolean (:double-authed session))
    false))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (when (= (:password user) password)
      (:objectid user))))

#_(defn login! [{:keys [session]} {:keys [username password]}]
  (if-let [id (authenticate-by-username username password)]
    (if (double-auth-required?)
      (-> (response/found "/double-auth")
          (assoc :session (assoc session :identity id))
          (assoc :session (assoc session :double-authed nil))
          (assoc :session (assoc session :double-auth-code (double-auth-code))))

      ;; TODO: Can't figure out how to do this in two steps
      (-> (response/found "/user/messages")
          (assoc :session (assoc session :identity id))))
    (response/unauthorized {:result :unauthorized
                            :message "login failure"})))

(defn login! [{:keys [session]} {:keys [username password]}]
    (if-let [id (authenticate-by-username username password)]
      (if (double-auth-required?)
        (-> (response/found "/double-auth")
            (assoc :session (assoc session :identity id :double-authed nil :double-auth-code (double-auth-code))))

        ;; TODO: Can't figure out how to do this in two steps
        (-> (response/found "/user/messages")
            (assoc :session (assoc session :identity id))))
      (response/unauthorized {:result :unauthorized
                              :message "login failure"})))


#_(defn login! [{:keys [session]} {:keys [username password]}]
  (if-let [id (authenticate-by-username username password)]
    (-> (response/found "/user/messages")
        (assoc :session (assoc session :identity id)))
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
