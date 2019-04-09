(ns bass4.session.create
  (:require [clj-time.core :as t]))

(defn new
  [user additional]
  (merge
    {:user-id         (:user-id user)
     :auth-re-auth?   nil
     :last-login-time (:last-login-time user)
     :session-start   (t/now)}
    additional))