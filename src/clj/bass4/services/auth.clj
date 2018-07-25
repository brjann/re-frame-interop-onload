(ns bass4.services.auth
  (:require [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [buddy.hashers :as hashers]
            [bass4.config :refer [env]]))

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
  (if-let [settings (db/bool-cols
                      db/get-double-auth-settings
                      {:user-id user-id}
                      [:sms? :email? :user-skip? :allow-skip? :allow-both?])]
    (let [{:keys [sms? email? user-skip? allow-skip?]} settings]
      (cond
        (and (not sms?) (not email?)) false
        (and allow-skip? user-skip?) false
        :else settings))
    false))

(defn- upgrade-password!
  [user]
  (if-not (empty? (:old-password user))
    (do
      (when-not (empty? (:password user))
        (throw (Exception. (str "User " (:user-id user) " has both new and old password"))))
      (let [algo          (env :password-hash)
            password-hash (hashers/derive (:old-password user) algo)]
        (log/debug algo)
        (db/update-password! {:user-id (:user-id user) :password password-hash})
        (assoc user :password password-hash)))
    user))

(defn- authenticate-user
  [user password]
  (let [user (upgrade-password! user)]
    (when-not (empty? (:password user))
      (try (when (hashers/check password (:password user))
             user)
           (catch Exception _)))))

(defn authenticate-by-user-id [user-id password]
  (when-let [user (db/get-user-by-user-id {:user-id user-id})]
    (authenticate-user user password)))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (authenticate-user user password)))

(defn register-user-login! [user-id]
  (db/update-last-login! {:user-id user-id})
  (db/update-login-count! {:user-id user-id}))

;; -------------
;;  DOUBLE AUTH
;; -------------


(defn not-authenticated? [session]
  (nil? (:identity session)))


(defn double-auth-no-code [session]
  ;; TODO: This comment doesn't make sense
  "Returns true if double auth is not required or if double auth code has not been created.
  Is used in the context where any of these are EXPECTED"
  (nil? (:double-auth-code session)))

(defn double-auth-done? [session]
  (:double-authed session))