-- :name set-lost-password-request-uid! :! :1
-- :doc
UPDATE c_participant SET
 `LostPasswordRequestUID` = :uid,
 LostPasswordRequestTime = UNIX_TIMESTAMP(:now)
 WHERE `ObjectId` = :user-id;


-- :name reset-lost-password-request-uid! :! :1
-- :doc
UPDATE c_participant SET
 `LostPasswordRequestUID` = NULL,
 LostPasswordRequestTime = NULL
 WHERE `ObjectId` = :user-id;


-- :name get-user-by-username-or-email :? :1
-- :doc retrieve a user given the username.
SELECT
  ObjectId,
  ObjectId AS `user-id`,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email,
  SMSNumber AS `sms-number`,
  DoubleAuthUseBoth AS 'double-auth-use-both?',
  ParentInterface AS `project-id`,
  from_unixtime(LastLogin) AS `last-login-time`,
  `Password`,
  `OldPassword` AS `old-password`
FROM c_participant
WHERE UserName = :username-or-email OR Email = :username-or-email;


-- :name get-user-by-lost-password-request-uid! :? :1
SELECT
  ObjectId,
  ObjectId AS `user-id`,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email,
  SMSNumber AS `sms-number`,
  DoubleAuthUseBoth AS 'double-auth-use-both?',
  ParentInterface AS `project-id`,
  from_unixtime(LastLogin) AS `last-login-time`,
  `Password`,
  `OldPassword` AS `old-password`
FROM c_participant
WHERE LostPasswordRequestUID = :uid AND LostPasswordRequestTime > UNIX_TIMESTAMP(:now) - :time-limit
