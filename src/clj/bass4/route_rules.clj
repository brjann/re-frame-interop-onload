(ns bass4.route-rules
  (:require [bass4.layout :as layout]
            [ring.util.http-response :as http-response]
            [clout.core :as clout]
            [clojure.tools.logging :as log]))


;; ------------------------
;;     RULES HANDLER
;; ------------------------

(defn match-rules
  "Rules are in format [{:uri clout-uri :rules [[pred val-true val-false]*}*]
   Returns with matched uri params from clout, {} if match but no params"
  [request rules]
  (let [matched (mapv (fn [rule]
                        (let [route (clout/route-compile (:uri rule))]
                          (assoc rule
                            :params
                            (clout/route-matches route (dissoc request :path-info))))) rules)]
    (filterv :params matched)))

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
                  (log/debug (:uri request) "predicate" (:name (meta pred)) res)
                  (if (= :ok res)
                    (recur (rest rules))
                    res))))]
    (cond
      (true? res)
      true

      (string? res)
      (if (= :get (:request-method request))
        (http-response/found res)
        (layout/error-400-page))

      (= 404 res)
      (layout/error-404-page)

      (= 403 res)
      (layout/error-403-page)

      :else
      (throw (Exception. (str "Rule returned illegal value " res))))))

(defn wrap-rules
  [rules]
  (fn [handler]
    (fn [request]
      (let [res (->> (match-rules request rules)
                     (flatten-matching-rules)
                     (eval-rules request))]
        #_(log/debug res)
        #_(handler request)
        (if (true? res)
          (handler request)
          res)))))
