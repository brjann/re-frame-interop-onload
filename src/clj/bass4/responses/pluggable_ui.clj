(ns bass4.responses.pluggable-ui
  (:require [ring.util.http-response :as http-response]
            [bass4.clients.core :as clients]
            [bass4.file-response :as file]))

(defn pluggable-ui?
  [& _]
  (and (some? (clients/client-setting [:pluggable-ui-path]))
       (clients/client-setting [:use-pluggable-ui?])))

(defn pluggable-ui
  [request]
  (if-not (pluggable-ui?)
    (http-response/not-found "This DB does not use pluggable UI.")
    (let [path (subs (:uri request) (count "/user/ui"))]
      (if (= "" path)
        (http-response/found "/user/ui/")
        (let [ui-path  (clients/client-setting [:pluggable-ui-path])
              _        (when-not ui-path
                         (throw (Exception. "No :pluggable-ui-path in config")))
              response (or (http-response/file-response path {:root ui-path})
                           (http-response/file-response "" {:root ui-path}))]
          (if (= 200 (:status response))
            (file/file-headers response)
            response))))))