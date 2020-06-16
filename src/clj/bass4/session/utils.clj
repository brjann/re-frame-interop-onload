(ns bass4.session.utils)

(defn assoc-out-session
  [response session-in merge-map]
  (let [session-out      (:session response)
        session-deleted? (and (contains? response :session) (empty? session-out))
        no-session?      (and (empty? session-in) (empty? session-out))]
    (if (or session-deleted? no-session?)
      response
      (assoc response :session (merge (or session-out
                                          session-in)
                                      merge-map)))))

(defn no-re-auth?
  [session]
  (or (not (:user-id session))
      (:external-login? session)))
