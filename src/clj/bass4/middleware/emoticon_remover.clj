(ns bass4.middleware.emoticon-remover
  (:require [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn remove-emoticons-s
  [s]
  (str/replace s #"[^\u0000-\uFFFF]" "\uFFFD"))

(defn remove-emoticons-m
  [m]
  (if (map? m)
    (walk/postwalk (fn [x] (if (and (map-entry? x)
                                    (string? (second x)))
                             [(first x) (remove-emoticons-s (second x))]
                             x))
                   m)
    m))

(defn remove-emoticons-mw
  [handler request]
  (let [body-params (:body-params request)]
    (handler (merge request
                    {:body-params  (cond
                                     (map? body-params) (remove-emoticons-m body-params)
                                     (string? body-params) (remove-emoticons-s body-params)
                                     :else body-params)
                     :params       (remove-emoticons-m (:params request))
                     :query-params (remove-emoticons-m (:query-params request))
                     :form-params  (remove-emoticons-m (:form-params request))}))))