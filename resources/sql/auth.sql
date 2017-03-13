-- :name get-user-by-username :? :1
-- :doc retrieve a user given the username.
SELECT * FROM c_participant
WHERE UserName = :username
