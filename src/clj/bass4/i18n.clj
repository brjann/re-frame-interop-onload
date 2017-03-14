(ns bass4.i18n
  (:require [selmer.parser :as parser]
            [taoensso.tempura :as tempura]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(def tempura-dictionary
  {:en
   {:missing ":en missing text"
    :submit "Submit"
    :date-time
             {:datetime-ns "yyyy-MM-dd HH:mm"}
    :messages
             {:sent-by "Sent by %1 on %2"
              :subject "Message subject"
              :text "Message text"
              :save-draft "Save draft"}}})

(defn tr
  ([resource-ids] (tr resource-ids []))
  ([resource-ids resource-args]
          (tempura/tr
            {:dict tempura-dictionary}
            [:en]
            resource-ids
            resource-args)))

