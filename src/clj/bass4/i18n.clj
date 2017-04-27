(ns bass4.i18n
  (:require [selmer.parser :as parser]
            [taoensso.tempura :as tempura]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(def tempura-dictionary
  {:en
   {:submit "Submit"
    :next "Next"
    :previous "Previous"
    :username "Username"
    :password "Password"
    :continue "Continue"
    :logout "Logout"
    :offline-warning "You seem to be offline. Please make sure you are online before posting."
    :date-time
             {:datetime-ns "yyyy-MM-dd HH:mm"}
    :messages
             {:sent-by "Sent by %1 on %2"
              :subject "Message subject"
              :text "Message text"
              :save-draft "Save draft"}
    :login
            {:login "Login"
             :please-username-password
                    "Please provide your username and password to log on to the system."
             :username-password-error "Wrong username or password"
             :please-double-auth "Please provide your double authentication code"
             :code "Code"
             :code-error "Wrong code provided"
             :re-auth "Authenticate again"
             :password-error "Wrong password"
             :please-re-auth "Please provide your password to continue to use the system."
             :please-re-auth-form "Your password is needed before you can submit your data."}}})

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

