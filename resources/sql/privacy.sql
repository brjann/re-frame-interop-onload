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


-- :name db-privacy-user-must-consent? :? :1
-- :doc
SELECT
  CASE
  WHEN (PrivacyNoticeMustConsent IS NULL)
    THEN 1
  ELSE
    PrivacyNoticeMustConsent
  END AS `must-consent?`
FROM c_project
WHERE ObjectId = 100;


-- :name project-privacy-user-must-consent? :? :1
-- :doc
SELECT
  CASE
  WHEN (PrivacyNoticeMustConsent IS NULL)
    THEN 1
  ELSE
    PrivacyNoticeMustConsent
  END AS `must-consent?`
FROM c_treatmentinterface
WHERE ObjectId = :project-id;
