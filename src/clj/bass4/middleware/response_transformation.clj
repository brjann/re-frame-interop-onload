(ns bass4.middleware.response-transformation
  (:require [bass4.http-utils :as h-utils]
            [bass4.error-pages :as error-pages]
            [bass4.utils :as utils]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]))



;; ----------------
;;  AJAX POST
;; ----------------

;; Redirects are followed by the ajax request. So POST route does not answer with
;; response/found (302). But rather with response/ok (200) and then a string with further
;; instructions. found [url]: the post was received and here is your new url.
;; This is achieved by middleware in the app that changes "302 url" responses to
;; "200 found url" responses - for ajax posts.
;; If page should just be reloaded, then the url returned should simply be "reload".

(defn found-200
  [response]
  (let [location (get-in response [:headers "Location"])]
    (-> response
        (assoc :status 200)
        (assoc :headers {"Content-Type" "text/plain; charset=utf-8"})
        (assoc :body (str "found " location)))))

(defn transform-mw
  [handler request]
  (let [ajax?      (h-utils/ajax? request)
        api?       (= "/api/" (utils/subs+ (:uri request) 0 5))
        get?       (= :get (:request-method request))
        post?      (= :post (:request-method request))
        ajax-post? (and ajax? post?)
        response   (handler request)
        status     (:status response)
        user-id    (get-in request [:session :user-id])]
    (cond
      (= 500 status)
      response

      ;; if ajax and response is 302, then send
      ;; the special found location response instead
      (and (or ajax-post? api?) (= 302 status))
      (found-200 response)

      ;; The user is trying to post to
      ;; forbidden and is logged in.
      (and ajax-post? (= 403 status) user-id)
      (assoc response :body "reload")

      ;; The user is trying to post to
      ;; forbidden and is not logged in.
      (and ajax-post? (= 403 status))
      (assoc response :body "login")

      (and (not ajax?) get? (= 403 status))
      (error-pages/error-403-page user-id (:body response))

      (and (not ajax?) get? (= 404 status))
      (error-pages/error-404-page (:body response))

      (and (not ajax?) get? (= 400 status))
      (error-pages/error-400-page (:body response))

      :else
      response)))