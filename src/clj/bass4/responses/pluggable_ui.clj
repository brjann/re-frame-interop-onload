(ns bass4.responses.pluggable-ui
  (:require [ring.util.http-response :as http-response]
            [bass4.clients.core :as clients]
            [bass4.file-response :as file]))

(defn pluggable-ui*?
  [client-name]
  (and (some? (clients/client-setting* client-name [:pluggable-ui-path] nil))
       (clients/client-setting* client-name [:use-pluggable-ui?] false)))

(defn pluggable-ui?
  [request & _]
  (and (some? (clients/client-setting [:pluggable-ui-path]))
       (clients/client-setting [:use-pluggable-ui?])
       (not (get-in request [:db :user :disable-pluggable-ui?]))))

(defn pluggable-ui
  [request base-path]
  (if-not (pluggable-ui? request)
    (http-response/not-found "This DB does not use pluggable UI.")
    (let [path (subs (:uri request) (count base-path))]
      (if (= "" path)
        (http-response/found (str base-path "/"))
        (let [ui-path  (clients/client-setting [:pluggable-ui-path])
              _        (when-not ui-path
                         (throw (Exception. "No :pluggable-ui-path in config")))
              response (or (http-response/file-response path {:root ui-path})
                           (http-response/file-response "" {:root ui-path}))]
          (if (= 200 (:status response))
            (file/file-headers response)
            response))))))