
-- :name set-lost-password-request-uid! :! :1
-- :doc
UPDATE c_participant SET
 `LostPasswordRequestUID` = :uid,
 LostPasswordRequestTime = UNIX_TIMESTAMP(NOW())
 WHERE `ObjectId` = :user-id
