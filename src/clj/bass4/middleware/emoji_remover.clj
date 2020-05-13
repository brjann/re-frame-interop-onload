(ns bass4.middleware.emoji-remover
  (:require [clojure.walk :as walk]
            [clojure.string :as str]))

(defn remove-emojis-s
  [s]
  (str/replace s #"[^\u0000-\uFFFF]" "\uFFFD"))

(defn remove-emojis-m
  [m]
  (if (map? m)
    (walk/postwalk (fn [x] (if (and (map-entry? x)
                                    (string? (second x)))
                             [(first x) (remove-emojis-s (second x))]
                             x))
                   m)
    m))

(defn remove-emojis-mw
  [handler request]
  (let [body-params (:body-params request)]
    (handler (merge request
                    {:body-params  (cond
                                     (map? body-params) (remove-emojis-m body-params)
                                     (string? body-params) (remove-emojis-s body-params)
                                     :else body-params)
                     :params       (remove-emojis-m (:params request))
                     :query-params (remove-emojis-m (:query-params request))
                     :form-params  (remove-emojis-m (:form-params request))}))))