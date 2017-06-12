
-- :name get-commons :? :*
-- :doc
SELECT * FROM common_log_pageloads
  LIMIT 1000;

INSERT INTO common_log_pageloads
(Time, DB, IPAddress, SQLTime, SQLMaxTime, UserId, RenderTime, ResponseSize, ErrorCount, ErrorMsgs, SourceFile, SessionStart, UserAgent)
VALUES(unix_timestamp(now()), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);