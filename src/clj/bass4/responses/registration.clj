(ns bass4.responses.registration
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [clojure.walk :as walk]
            [bass4.services.registration :as reg-service]
            [bass4.responses.auth :as res-auth]
            [clj-time.core :as t]
            [bass4.utils :refer [filter-map]]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]))

(defn- captcha-session
  [project-id]
  (let [{:keys [filename digits]} (captcha/captcha!)]
    (-> (response/found (str "/registration/" project-id "/captcha"))
        (assoc :session {:captcha-filename filename :captcha-digits digits}))))

(defn- captcha-page
  [project-id filename]
  (let [content (reg-service/captcha-content project-id)]
    (layout/render
      "registration-captcha.html"
      {:content  content
       :filename filename})))

(defn captcha
  [project-id session]
  (if (:captcha-ok session)
    (response/found (str "/registration/" project-id))
    (let [filename (:captcha-filename session)
          digits   (:captcha-digits session)]
      (if (and filename digits)
        (captcha-page project-id filename)
        (captcha-session project-id)))))

(defn validate-captcha
  [project-id captcha session]
  (if-let [digits (:captcha-digits session)]
    (if (= digits captcha)
      (-> (response/found (str "/registration/" project-id ""))
          (assoc :session {:captcha-ok true}))
      (layout/error-422 "error"))
    (response/found (str "/registration/" project-id "/captcha"))))

(defn registration
  [project-id]
  (layout/render "registration-form.html"
                 {:registration-content (reg-service/registration-content project-id)}))

(defn- map-fields
  [fields-mapping fields]
  (->> fields-mapping
       (map #(vector (first %) (get fields (second %))))
       (into {})
       (filter-map identity)
       (walk/keywordize-keys)))

(defn handle-registration
  [project-id fields]
  (let [{:keys [fields-mapping group]} (reg-service/registration-params project-id)
        field-values (map-fields fields-mapping fields)]
    (if (seq field-values)
      (let [user-id (reg-service/create-user! project-id field-values group)]
        (-> (response/found "/user")
            (assoc :session (res-auth/create-new-session
                              {:user-id user-id}
                              {:double-authed true}
                              true))))
      (layout/error-400-page))))

;; TODO: Tests for registration and following assessments
;; TODO: Duplicate username
;; TODO: Validate email and sms