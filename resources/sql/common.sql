
-- :name get-commons :? :*
-- :doc
SELECT * FROM common_log_pageloads
  LIMIT 1000;

-- :name save-pageload! :! :1
-- :doc
INSERT INTO common_log_pageloads
  (Time,
  DB,
  IPAddress,
  SQLTime,
  SQLMaxTime,
  SQLOps,
  UserId,
  RenderTime,
  ResponseSize,
  PHPVersion,
  ErrorCount,
  ErrorMsgs,
  SourceFile,
  SessionStart,
  UserAgent,
  Method,
  `Status`,
  Info)
VALUES
  (unix_timestamp(now()),
  :db-name,
  :remote-ip,
  :sql-time,
  :sql-max-time,
  :sql-ops,
  :user-id,
  :render-time,
  :response-size,
  :clojure-version,
  :error-count,
  :error-messages,
  :source-file,
  :session-start,
  :user-agent,
  :method,
  :status,
  :info);