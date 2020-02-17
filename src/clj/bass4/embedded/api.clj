(ns bass4.embedded.api
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :as http-response]
            [bass4.embedded.middleware :as embedded-mw]
            [bass4.layout :as layout]
            [bass4.route-rules :as route-rules]
            [bass4.routes.user :as user-routes]
            [bass4.responses.messages :as messages-response]
            [bass4.treatment.responses :as treatment-response]
            [bass4.responses.privacy :as privacy-response]
            [bass4.responses.auth :as auth-response]
            [bass4.module.api :as module-api]
            [bass4.api-coercion :as api]
            [bass4.treatment.builder :as treatment-builder]
            [bass4.session.timeout :as session-timeout]
            [bass4.utils :as utils]
            [bass4.i18n :as i18n]
            [bass4.clients.core :as clients]))

(def api-routes
  (context "/embedded/api" [:as request]
    (GET "/unlock-api" []
      :summary "Gives full permissions for all /embedded/api requests. Can only be used in dev or debug mode."
      (if (clients/debug-mode?)
        (-> (http-response/ok)
            (assoc :session (merge (:session request)
                                   {:csrf-disabled           true
                                    ::embedded-mw/debug-api? true})))
        (http-response/forbidden "Not in debug or dev mode")))
    (GET "/test" []
      :summary ""
      (layout/text-response "HELLO!"))))