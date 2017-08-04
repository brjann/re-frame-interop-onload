-- :name get-user-by-user-id :? :1
-- :doc retrieve a user given the id.
SELECT
  ObjectId,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email,
  SMSNumber AS `sms-number`,
  DoubleAuthUseBoth AS 'double-auth-use-both',
  ParentInterface AS `project-id`,
  `Password`
FROM c_participant
WHERE ObjectId = :user-id;


-- :name get-user-by-username :? :1
-- :doc retrieve a user given the username.
SELECT
  ObjectId,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email,
  SMSNumber AS `sms-number`,
  DoubleAuthUseBoth AS 'double-auth-use-both',
  ParentInterface AS `project-id`,
  `Password`
FROM c_participant
WHERE UserName = :username;

-- :name get-double-auth-settings :? :1
-- :doc
SELECT
  cp.DoubleAuthSkip AS `user-skip`,
  ct.DoubleAuthUseSMS AS `sms`,
  ct.DoubleAuthUseEmail AS `email`,
  ct.DoubleAuthAllowSkip AS `allow-skip`,
  ct.DoubleAuthAllowBothMethods AS `allow-both`
FROM c_participant AS cp
  JOIN c_treatmentinterface AS ct
    ON cp.ParentInterface = ct.ObjectId
WHERE cp.objectid = :user-id;