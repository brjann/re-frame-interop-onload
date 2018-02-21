(ns bass4.responses.registration
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [bass4.services.registration :as reg-service]
            [clj-time.core :as t]
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

(defn handle-registration
  [project-id]
  (layout/render "registration-form.html"
                 {:registration-content (reg-service/registration-content project-id)}))