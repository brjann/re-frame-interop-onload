(ns bass4.http-errors)

(defn error-422
  "https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
  422 Unprocessable Entity (WebDAV; RFC 4918)
  The request was well-formed but was unable to be followed due to semantic errors.

  Used to communicate back to form that there was something wrong with
  the posted data. For example erroneous username-password combination"
  ([] (error-422 ""))
  ([body]
   {:status  422
    :headers {"Content-Type" "text/plain; charset=utf-8"}
    :body    body}))

(defn error-429
  "https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
  429 Too Many Requests (RFC 6585)
  The user has sent too many requests in a given amount of time. Intended for use with rate-limiting schemes

  Used to communicate back to authentication form that too many failed requests
  have been made with body equal to number of second until request can be retried"
  ([] (error-429 ""))
  ([secs]
   {:status  429
    :headers {"Content-Type" "text/plain; charset=utf-8"}
    :body    (str secs)}))