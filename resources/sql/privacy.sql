-- :name get-project-privacy-notice :? :1
-- :doc
SELECT
    PrivacyNotice as `privacy-notice`
FROM c_treatmentinterface
WHERE ObjectId = :project-id;

-- :name get-db-privacy-notice :? :1
-- :doc
SELECT
    PrivacyNotice as `privacy-notice`
FROM c_project
WHERE ObjectId = 100;