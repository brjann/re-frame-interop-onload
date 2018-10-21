-- :name get-project-privacy-notice :? :1
-- :doc

SELECT
    `notice` as `notice-text`,
    `id` as `id`
FROM privacy_notices
WHERE id = (SELECT max(id) FROM privacy_notices WHERE parent_id = :project-id);


-- :name get-db-privacy-notice :? :1
-- :doc
SELECT
    `notice` as `notice-text`,
    `id` as `id`
FROM privacy_notices
WHERE id = (SELECT max(id) FROM privacy_notices WHERE parent_id = 100);


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


-- :name get-no-consent-flags :? :*
-- :doc
SELECT
  ObjectId as `flag-id`
FROM c_flag
WHERE `Issuer` = 'no-consent'
  AND (ClosedAt = 0 OR ClosedAt IS NULL)
  AND `ParentId` = :user-id;



-- :name privacy-notice-exists? :? :1
-- :doc
SELECT
  CASE
  WHEN (count(*) > 0)
    THEN 1
  ELSE
    0
  END AS `exists?`
FROM privacy_notices
WHERE
  (id = (SELECT max(id) FROM privacy_notices WHERE parent_id = 100))
  OR
  (id = (SELECT max(id) FROM privacy_notices WHERE parent_id = :project-id))