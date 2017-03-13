(ns bass4.services.messages
  (:require [bass4.layout :as layout]
            [bass4.db.core :as db]))

(defn messages-page [{:keys [identity]} errors]
  (let [user (db/get-user {:id identity})
        messages (db/get-all-messages {:user-id identity})]
    (layout/render
      "messages.html"
      {:user user
       :title "Messages"
       :active_messages true
       :messages messages
       :errors errors})))

