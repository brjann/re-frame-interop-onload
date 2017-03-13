-- :name get-user :? :1
-- :doc retrieve a user given the id.
SELECT * FROM c_participant
WHERE ObjectId = :id


-- :name get-user-by-username :? :1
-- :doc retrieve a user given the username.
SELECT * FROM c_participant
WHERE UserName = :username
