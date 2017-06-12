(ns bass4.request-state
  (:require [clojure.tools.logging :as log]
            [bass4.utils :refer [time+] :as utils]))

(def ^:dynamic *request-state*)

#_(defn request-state-wrapper
  [handler]
  (fn [request]
    (binding [*request-state* (atom {})]
      (let [res (handler request)]
        (log/debug "hejsan hoppsan")
        (log/debug *request-state*)
        res))))

(defn swap-state!
  [key f val-if-empty]
  (utils/swap-key! *request-state* key f val-if-empty))

(defn set-state!
  [key val]
  (utils/set-key! *request-state* key val))

(defn request-state-wrapper
  [handler request]
  (binding [*request-state* (atom {})]
    (let [{:keys [val time]} (time+ (handler request))]
      (set-state! :render-time (int time))
      (log/info @*request-state*)
      val)))

;'Time' => $iTime,
;'DB' => PROJECT_NAME,
;'IPAddress' => (MODE_CLI ? 'CLI' : $_SERVER['REMOTE_ADDR']),
;'ObjectCount' => $iObjectCount,
;'LinkCount' => $iLinkCount,
;'ParticipantCount' => $iParticipantCount,
;'SQLTime' => $fSQLTime,
;'SQLMaxTime' => $fSQLMaxTime,
;'SQLSize' => $iSQLSize,
;'SQLMaxSize' => $iSQLMaxSize,
;'SQLOps' => $iSQLOps,
;'RetrievedObjects' => $iRetrievedObjects,
;'LoadedObjects' => $iLoadedObjects,
;'MemoryUsage' => $iMemoryUsage,
;'MemoryAvailable' => $iMemoryAvailable,
;'UserId' => $UserId,
;'UserType' => $UserType,
;'RenderTime' => $fRenderTime,
;'ResponseSize' => $iResponseSize,
;'DiskFreeSpace' => $fDiskFreeSpace,
;'PHPVersion' => $sPHPVersion,
;'MySQLVersion' => $sMySQLVersion,
;'ErrorCount' => $iErrorCount,
;'ErrorMsgs' => $sErrorMsgs,
;'LogFile' => $sLogFile,
;'SourceFile' => (MODE_CLI ? basename($_SERVER["SCRIPT_FILENAME"]) : getSourceFile()),
;'SessionStart' => S()->SessionStart,
;'UserAgent' => (MODE_CLI ? 'CLI' : $_SERVER['HTTP_USER_AGENT'])