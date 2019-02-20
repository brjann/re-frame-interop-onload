(ns bass4.route-rules
  (:require [bass4.layout :as layout]
            [ring.util.http-response :as http-response]
            [bass4.clout-cache :as clout-cache]
            [clojure.tools.logging :as log]
            [bass4.config :as config]))


;; ------------------------
;;     RULES HANDLER
;; ------------------------

(defn match-rules
  "Rules are in format [{:uri clout-uri :rules [[pred val-true val-false]*}*]
   Returns with matched uri params from clout, {} if match but no params"
  [request rules]
  (let [match-res (mapv (fn [rule]
                          (assoc rule
                            :params
                            (clout-cache/route-matches (:uri rule) (dissoc request :path-info))))
                        rules)
        matches   (filterv :params match-res)]
    #_(when (seq matches)
        (log/debug "Found rules matching" (:uri request)))
    matches))

(defn flatten-matching-rules
  "Rules are in format [{:uri clout-uri :rules [[pred val-true val-false]* :params uri-params}*]
   Returns vector every rule associated with their params
   [{:rule [pred val-true val-false] :params uri-params}]*"
  [rules]
  (->>
    rules
    (mapv
      (fn [rule]
        (mapv #(hash-map :rule % :params (:params rule))
              (:rules rule))))
    (flatten)))

(defn eval-rules
  "Rules are in format {:rule [pred val-true val-false] :params uri-params}"
  [request rules]
  (let [res (loop [rules rules]
              (if (empty? rules)
                true
                (let [rule (first rules)
                      [pred pred-true pred-false] (:rule rule)
                      res  (if (pred request (:params rule))
                             pred-true
                             pred-false)]
                  #_(log/debug (:uri request) "predicate" (:name (meta pred)) res)
                  (if (or (true? res) (= :ok res))
                    (recur (rest rules))
                    res))))]
    (cond
      (true? res)
      true

      ;; If redirect to present uri - return true avoid infinite redirects
      (and (string? res) (= (:uri request) res))
      true

      (string? res)
      (if (= :get (:request-method request))
        (http-response/found res)
        (http-response/bad-request))

      (= 404 res)
      (http-response/not-found)

      (= 403 res)
      (http-response/forbidden)

      :else
      (throw (Exception. (str "Rule returned illegal value " res))))))

(defn wrap-rules
  [rules]
  (fn [handler]
    (fn [request]
      (let [res (->> rules
                     (match-rules request)
                     (flatten-matching-rules)
                     (eval-rules request))]
        (if (true? res)
          (handler request)
          res)))))

(def compiled-route-middlewares (atom {}))

(defn- compile-middleware
  [handler route-mws]
  (loop [v         handler
         route-mws route-mws]
    (if (empty? route-mws)
      v
      (recur ((first route-mws) v) (rest route-mws)))))

(defn wrap-route-mw
  [handler uri-pattern & route-mws]
  (fn [request]
    (if (some #(clout-cache/route-matches % request) uri-pattern)
      (let [dev?    (config/env :dev)
            comp-mw (if (and (not dev?) (contains? @compiled-route-middlewares uri-pattern))
                      (get @compiled-route-middlewares uri-pattern)
                      (let [comp-mw (compile-middleware handler route-mws)]
                        (swap! compiled-route-middlewares assoc uri-pattern comp-mw)
                        comp-mw))]
        #_(log/debug "Matched request to" uri-pattern)
        (comp-mw request))
      (handler request))))