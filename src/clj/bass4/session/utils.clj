(ns bass4.session.utils)

(defn assoc-out-session
  [response session-in merge-map]
  (let [session-out      (:session response)
        session-deleted? (and (contains? response :session) (empty? session-out))]
    (if session-deleted?
      response
      (assoc response :session (merge (or session-out
                                          session-in)
                                      merge-map)))))