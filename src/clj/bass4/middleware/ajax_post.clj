(ns bass4.middleware.ajax-post
  (:require [bass4.request-state :as request-state]
            [clojure.string :as string]))



;; ----------------
;;  AJAX POST
;; ----------------

;; Redirects are followed by the ajax request. So POST route does not answer with
;; response/found (302). But rather with response/ok (200) and then a string with further
;; instructions. found [url]: the post was received and here is your new url.
;; This is achieved by middleware in the app that changes "302 url" responses to
;; "200 found url" responses - for ajax posts.
;; If page should just be reloaded, then the url returned should simply be "reload".

(defn- is-ajax-post?
  [request]
  (let [requested-with (get (:headers request) "x-requested-with" "")
        request-method (:request-method request)]
    (and (= (string/lower-case requested-with) "xmlhttprequest")
         (= request-method :post))))

(defn- ajax-found
  [response]
  (let [location (get (:headers response) "Location")
        new-map  {:status  200
                  :headers {}
                  :body    (str "found " location)}]
    (merge response new-map)))

(defn- ajax-403-logged-in
  [request response]
  (request-state/record-error!
    (str "403 error when posting to " (:uri request)
         "\nUser " (get-in request [:session :identity])))
  (merge response {:status  403
                   :headers {}
                   :body    "reload"}))

(defn- ajax-403-not-logged-in
  [response]
  (merge response {:status  403
                   :headers {}
                   :body    "login"}))

(defn ajax-post
  [handler request]
  (let [ajax-post? (is-ajax-post? request)
        response (handler request)]
    (cond

      ;; if ajax and response is 302, then send
      ;; the special found location response instead
      (and ajax-post? (= (:status response) 302)) (ajax-found response)

      ;; The user is trying to post to
      ;; forbidden but is not logged out.
      ;; Save this as an error
      (and ajax-post?
           (= (:status response) 403)
           (not= nil (get-in request [:session :identity])))
      (ajax-403-logged-in request response)

      (and ajax-post? (= (:status response) 403))
      (ajax-403-not-logged-in response)

      :else
      response)))



