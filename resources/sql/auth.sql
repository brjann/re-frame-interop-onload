-- :name get-user-by-user-id :? :1
-- :doc retrieve a user given the id.
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
WHERE ObjectId = :user-id;


-- :name get-user-by-username :? :1
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
WHERE UserName = :username;

-- :name get-user-by-participant-id :? :*
-- :doc retrieve a user given the id.
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
WHERE ParticipantId = :participant-id;

-- :name get-double-auth-settings :? :1
-- :doc
SELECT
  cp.DoubleAuthSkip AS `user-skip?`,
  ct.DoubleAuthUseSMS AS `sms?`,
  ct.DoubleAuthUseEmail AS `email?`,
  ct.DoubleAuthAllowSkip AS `allow-skip?`,
  ct.DoubleAuthAllowBothMethods AS `allow-both?`
FROM c_participant AS cp
  JOIN c_treatmentinterface AS ct
    ON cp.ParentInterface = ct.ObjectId
WHERE cp.objectid = :user-id;

-- :name update-password! :! :1
-- :doc
UPDATE c_participant
SET `Password` = :password,
    `OldPassword` = ''
WHERE ObjectId=:user-id;

-- :name update-last-login! :! :1
-- :doc
UPDATE c_participant
SET LastLogin = unix_timestamp(now())
WHERE ObjectId=:user-id;

-- :name update-login-count! :! :1
-- :doc
UPDATE c_participant
SET LoginCount = LoginCount + 1
WHERE ObjectId=:user-id;


-- :name get-user-by-quick-login-id :? :*
-- :doc
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
  `OldPassword` AS `old-password`,
  from_unixtime(QuickLoginTimestamp) AS `quick-login-timestamp`
FROM c_participant
WHERE QuickLoginPassword = :quick-login-id;
