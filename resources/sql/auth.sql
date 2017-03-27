-- :name get-user-by-user-id :? :1
-- :doc retrieve a user given the id.
SELECT * FROM c_participant
WHERE ObjectId = :user-id;


-- :name get-user-by-username :? :1
-- :doc retrieve a user given the username.
SELECT * FROM c_participant
WHERE UserName = :username
