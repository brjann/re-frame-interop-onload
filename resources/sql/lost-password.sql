-- :name get-lost-password-method :? :1
-- :doc
SELECT
  LostPasswordMethod AS `lost-password-method`
FROM c_project
WHERE ObjectId = 100;


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
  /*~
  bass4.db.core/sql-user-fields
  ~*/
FROM c_participant
WHERE UserName = :username-or-email OR Email = :username-or-email;


-- :name get-user-by-lost-password-request-uid! :? :1
SELECT
  /*~
  bass4.db.core/sql-user-fields
  ~*/
FROM c_participant
WHERE LostPasswordRequestUID = :uid AND LostPasswordRequestTime > UNIX_TIMESTAMP(:now) - :time-limit


-- :name get-user-by-lost-password-request-uid! :? :1
SELECT
  /*~
  bass4.db.core/sql-user-fields
  ~*/
FROM c_participant
WHERE LostPasswordRequestUID = :uid AND LostPasswordRequestTime > UNIX_TIMESTAMP(:now) - :time-limit
