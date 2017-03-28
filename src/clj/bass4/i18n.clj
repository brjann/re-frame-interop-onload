(ns bass4.i18n
  (:require [selmer.parser :as parser]
            [taoensso.tempura :as tempura]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(def tempura-dictionary
  {:en
   {:submit "Submit"
    :username "Username"
    :password "Password"
    :date-time
             {:datetime-ns "yyyy-MM-dd HH:mm"}
    :messages
             {:sent-by "Sent by %1 on %2"
              :subject "Message subject"
              :text "Message text"
              :save-draft "Save draft"}
    :login
            {:login "Login"
             :please-provide
                    "Please provide your username and password to log on to the system."
             :password-error "Wrong username or password"}}})

(defn tr
  ([resource-ids] (tr resource-ids []))
  ([resource-ids resource-args]
          (tempura/tr
            {:dict tempura-dictionary
             :missing-resource-fn
                   (fn
                     [{:keys [opts locales resource-ids resource-args]}]
                     (str "Missing translation keys: " resource-ids))}
            [:en]
            resource-ids
            resource-args)))

