-- :name get-user-by-user-id :? :1
-- :doc retrieve a user given the id.
SELECT * FROM c_participant
WHERE ObjectId = :user-id;


-- :name get-user-by-username :? :1
-- :doc retrieve a user given the username.
SELECT * FROM c_participant
WHERE UserName = :username;

-- :name get-double-auth-settings :? :1
-- :doc
SELECT
  cp.DoubleAuthSkip AS `user-skip`,
  ct.DoubleAuthUseSMS AS `sms`,
  ct.DoubleAuthUseEmail AS `email`,
  ct.DoubleAuthAllowSkip AS `allow-skip`
FROM c_participant AS cp
  JOIN c_treatmentinterface AS ct
    ON cp.ParentInterface = ct.ObjectId
WHERE cp.objectid = :user-id;