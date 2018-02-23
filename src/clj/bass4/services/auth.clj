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

(defn letters-digits
  [length]
  (clojure.string/join
    ""
    (map
      #(get password-chars %1)
      (repeatedly length #(rand-int (- (count password-chars) 1))))))

(defn double-auth-code []
  (letters-digits 3))

(defn double-auth-required? [user-id]
  (if-let [settings (db/get-double-auth-settings {:user-id user-id})]
    (let [{:keys [sms email user-skip allow-skip]} settings]
      (cond
        (and (zero? sms) (zero? email)) false
        (and (pos? allow-skip) (pos? user-skip)) false
        :else settings))
    false))

(defn authenticate-by-user-id [user-id password]
  (when-let [user (db/get-user-by-user-id {:user-id user-id})]
    (when (= (:password user) password)
      (:objectid user))))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (when (= (:password user) password)
      (assoc user :user-id (:objectid user))
      #_(:objectid user))))

(defn register-user-login! [user-id]
  (db/update-last-login! {:user-id user-id})
  (db/update-login-count! {:user-id user-id}))

;; -------------
;;  DOUBLE AUTH
;; -------------


(defn not-authenticated? [session]
  (nil? (:identity session)))


(defn double-auth-no-code [session]
  "Returns true if double auth is not required or if double auth code has not been created.
  Is used in the context where any of these are EXPECTED"
  (nil? (:double-auth-code session)))

(defn double-auth-done? [session]
  (:double-authed session))


;; -------------
;;  AUTH TIMEOUT
;; -------------


