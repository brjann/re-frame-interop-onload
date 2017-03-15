(ns bass4.views.messages
  (:require [bass4.layout :as layout]))

(defn messages-page [user messages draft]
  (layout/render
    "messages.html"
    {:user user
     :title "Messages"
     :active_messages true
     :messages messages
     :draft draft}))