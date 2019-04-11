
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
  `time` AS `time`
FROM common_log_failed_logins
WHERE
   `time` > (:time - :attack-interval);


-- :name clear-failed-logins! :! :1
-- :doc Used for testing
TRUNCATE common_log_failed_logins;