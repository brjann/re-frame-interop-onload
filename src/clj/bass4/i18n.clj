(ns bass4.i18n
  (:require [selmer.parser :as parser]
            [taoensso.tempura :as tempura]))

(def tempura-dictionary
  {:en
   {:missing ":en missing text"
    :submit "Submit"
    :messages
             {:sent-by "Sent by %1 on %2"
              :subject "Message subject"
              :text "Message text"
              :save-draft "Save draft"}}})

(defn tr [resource-ids resource-args]
  (tempura/tr
    {:dict tempura-dictionary}
    [:en]
    resource-ids
    resource-args))

