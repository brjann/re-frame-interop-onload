
-- :name save-failed-login! :! :1
-- :doc
INSERT INTO common_log_failed_logins
(`DB`, `Time`, `UserId`, `IPaddress`, `Type`, `UserName`)
VALUES(:db, :time, :user-id, :ip-address, :type, :username);

-- :name get-failed-logins :? :*
-- :doc
SELECT
  IPAddress as `ip-address`,
  DB,
  `Type`,
  UNIX_TIMESTAMP(`time`) AS `time`
FROM common_log_failed_logins
WHERE
  FROM_UNIXTIME(`time`) > DATE_SUB(:time, INTERVAL 15 MINUTE);


-- :name clear-failed-logins! :! :1
-- :doc Used for testing
TRUNCATE common_log_failed_logins;