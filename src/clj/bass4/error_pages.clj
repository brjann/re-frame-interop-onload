(ns bass4.error-pages
  (:require [selmer.parser :as parser]
            [bass4.i18n :as i18n]))

;; ---------------------------------------------
;; DO NOT INVOKE THESE FUNCTIONS DIRECTLY.
;; USE http-response/xxx
;; BASS takes care of invoking them if necessary
;; ---------------------------------------------

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  ([error-details] (error-page error-details "error.html"))
  ([error-details html]
   {:status  (:status error-details)
    :headers {"Content-Type" "text/html; charset=utf-8"}
    :body    (parser/render-file html error-details)}))

(defn error-403-page
  ([] (error-403-page nil))
  ([user-id] (error-403-page user-id (i18n/tr [:error/not-authorized-longer])))
  ([user-id message]
   (error-page {:status  403
                :title   (i18n/tr [:error/not-authorized])
                :message message}
               (if user-id "error-back-or-login.html" "error-login.html"))))

(defn error-400-page
  ([] (error-400-page nil))
  ([message]
   (error-page {:status  400
                :title   "Bad request!"
                :message message})))

(defn error-404-page
  ([] (error-404-page (i18n/tr [:error/page-not-found-longer])))
  ([message]
   (error-page {:status  404
                :title   (i18n/tr [:error/page-not-found])
                :message message}
               "error-back-or-login.html")))