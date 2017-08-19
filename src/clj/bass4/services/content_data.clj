(ns bass4.services.content-data
  (:require [bass4.db.core :as db]))

(defn reduce-content-map [m [x & xs :as s]]
  (if (empty? s)
    m
    (recur (merge m (assoc m (keyword (:valuename x)) (:value x))) xs)))

(defn unflatten-content-data
  [s]
  (reduce (fn [m x] (update-in m [(keyword (:dataname x))] #(conj % x))) {} s))

(defn content-data-transform [content-data]
  {(key content-data) (->> (val content-data)
                           (map #(select-keys % [:valuename :value]))
                           (reduce-content-map {}))})


(defn get-content-data [data-owner-id data-names]
  (reduce merge
          (map content-data-transform
               (unflatten-content-data
                 (db/get-content-data {:data-owner-id data-owner-id :data-names data-names})))))
;
;
;
;(defn worksheets-params [worksheet]
;  (let [data-name (:dataname worksheet)
;        treatment-access-id (:treatmentaccessid (get-current-treatment identity))]
;    {:worksheet worksheet
;     :data-name data-name
;     :worksheet-data ((keyword data-name) (get-content-data treatment-access-id [data-name]))}))
;
;(defn split-first [pair]
;  (let [splitted-list (clojure.string/split (first pair) #"\.")]
;    [(first splitted-list)
;     (clojure.string/join "." (rest splitted-list))
;     (second pair)]))
;
;(defn remove-identical-data [string-map old-data]
;  (filter
;    (fn [[data-name value-name value]]
;      (let [old-value (get-in old-data [(keyword data-name) (keyword value-name)])]
;        (if (= old-value nil)
;          (not= value "")
;          (not= old-value value))
;        ))
;    string-map))
;
;(defn add-data-time-and-owner [string-map treatment-access-id]
;  (map #(concat
;          [treatment-access-id
;           (quot (System/currentTimeMillis) 1000)]
;          %) string-map))
;
;(defn save-content-data!
;  [data-map treatment-access-id]
;  (let [string-map (map split-first data-map)
;        data-names (distinct (map first string-map))
;        old-data (get-content-data treatment-access-id data-names)
;        save-data (add-data-time-and-owner (remove-identical-data string-map old-data) treatment-access-id)]
;    (if (> (count save-data) 0)
;      (do (db/save-content-data! {:content-data save-data})
;          (str "Yeah! " (count save-data) " rows saved"))
;      (str "No rows saved"))))
;
;(defn handle-worksheet-submit [params {:keys [identity]}]
;  (let [current-treatment (get-current-treatment identity)
;        treatment-access-id (:treatmentaccessid current-treatment)
;        post-data (:content-data params)
;        ;; TODO: Add check for nil in post-data - else null pointer exception
;        data-map (into [] (json/read-str post-data))]
;    (save-content-data! data-map treatment-access-id)))