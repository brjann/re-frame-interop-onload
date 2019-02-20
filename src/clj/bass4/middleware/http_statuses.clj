(ns bass4.middleware.http-statuses
  (:require [bass4.request-state :as request-state]
            [bass4.http-utils :as h-utils]
            [bass4.error-pages :as error-pages]))



;; ----------------
;;  AJAX POST
;; ----------------

;; Redirects are followed by the ajax request. So POST route does not answer with
;; response/found (302). But rather with response/ok (200) and then a string with further
;; instructions. found [url]: the post was received and here is your new url.
;; This is achieved by middleware in the app that changes "302 url" responses to
;; "200 found url" responses - for ajax posts.
;; If page should just be reloaded, then the url returned should simply be "reload".


(defn- ajax-found
  [response]
  (let [location (get (:headers response) "Location")
        new-map  {:status  200
                  :headers {}
                  :body    (str "found " location)}]
    (merge response new-map)))

(defn http-statuses-mw
  [handler request]
  (let [ajax?      (h-utils/ajax? request)
        get?       (= :get (:request-method request))
        post?      (= :post (:request-method request))
        ajax-post? (and ajax? post?)
        response   (handler request)
        status     (:status response)
        user-id    (get-in request [:session :user-id])]
    (cond
      ;; If ajax and method is get
      ;; then just forward the response
      (and get? ajax?)
      response

      ;; if ajax and response is 302, then send
      ;; the special found location response instead
      (and ajax-post? (= 302 status))
      (ajax-found response)

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