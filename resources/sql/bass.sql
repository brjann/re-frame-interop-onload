-- :name create-bass-object! :? :1
-- :doc Create a new BASS object and return it's ObjectId
CALL create_bass_objects(:class-name, :parent-id, :property-name, 1);

-- :name create-bass-objects-without-parent! :? :1
-- :doc Create many new BASS objects and return the last's ObjectId
CALL create_bass_objects_without_parent(:class-name, :property-name, :count);

-- :name update-objectlist-parent! :! :1
-- :doc Update an object with new parent in objectlist
UPDATE objectlist
SET ParentId = :parent-id
WHERE ObjectId=:object-id

-- :name delete-object-from-objectlist! :! :1
-- :doc Update an object with new parent in objectlist
DELETE FROM objectlist
WHERE ObjectId=:object-id;

-- :name get-new-round-id! :? :1
-- :doc C
CALL new_assessment_round_id();

-- :name get-db-title :? :1
SELECT title
FROM c_project
WHERE ObjectId=100;

-- :name get-sms-sender :? :1
SELECT SMSSender AS `sms-sender`
FROM c_project
WHERE ObjectId=100;

-- :name get-contact-info :? :1
SELECT
  cp.Email AS `db-email`,
  ct.DefaultEmailRecipient AS `project-email`
FROM c_project AS cp LEFT JOIN c_treatmentinterface AS ct ON ct.ObjectId = :project-id
WHERE cp.ObjectId=100;

-- :name ext-login-settings :? :1
SELECT ExternalLoginAllowed AS `allowed?`,
  ExternalLoginIps AS ips
FROM c_project
WHERE ObjectId=100;

-- :name link-property-reverse! :! :1
-- :doc
INSERT IGNORE INTO links_properties_reverse
(LinkeeId, PropertyName, LinkerClass) VALUES (:linkee-id, :property-name, :linker-class);


-- :name get-project-participant-collection :? :1
SELECT ObjectId AS `collection-id`
FROM c_participantscollection
WHERE ParentId=:project-id;


-- :name update-user-properties! :! :1
-- :doc This is SQL injection safe!
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
UPDATE c_participant
SET
  /*~
  (string/join ","
    (for [[field _] (:updates params)]
      (str "`" (name field) "`"
        " = :v:updates." (name field))))
  ~*/
  where ObjectId=:user-id;



-- :name update-object-properties! :! :1
-- :doc This is SQL injection safe!
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
UPDATE :i:table-name
SET
  /*~
  (string/join ","
    (for [[field _] (:updates params)]
      (str "`" (name field) "`"
        " = :v:updates." (name field))))
  ~*/
  where ObjectId = :object-id;


-- :name create-bass-link! :! :1
-- :doc
CALL create_bass_link(:linker-id, :linkee-id, :link-property, :linker-class, :linkee-class);

-- :name get-time-zone :? :1
SELECT @@session.time_zone AS `time-zone`;


-- :name get-quick-login-settings :? :1
-- :doc
SELECT
  QuickLoginAllowed AS `allowed?`,
  QuickLoginDeadline AS `expiration-days`
FROM c_project
WHERE ObjectId = 100;

-- :name get-php-session :? :1
-- :doc
SELECT
  LastActivity AS `last-activity`,
  UserId AS `user-id`
FROM sessions
WHERE SessId = :php-session-id;


-- :name update-php-session-last-activity! :! :1
-- :doc
UPDATE sessions
  SET LastActivity = :now
WHERE SessId = :php-session-id;

-- :name get-staff-timeouts :? :1
-- :doc
SELECT
  (CASE
     WHEN TherapistTimeout IS NULL
         THEN 1800
     ELSE TherapistTimeout
     END ) AS `re-auth-timeout`,
  (CASE
     WHEN AbsoluteTimeout IS NULL
         THEN 28800
     ELSE AbsoluteTimeout
     END ) AS `absolute-timeout`
FROM c_project
WHERE ObjectId = 100;

-- :name log-bankid! :! :1
-- :doc
INSERT INTO
  bankid_log
    (`uid`,
    `personal-number`,
    `order-ref`,
    `auto-start-token`,
    `status`,
    `hint-code`,
    `http-status`,
    `user`,
    `device`,
    `cert`,
    `signature`,
    `ocsp-response`,
    `error-code`,
    `details`,
    `exception`,
    `other`)
  VALUES(
    :uid,
    :personal-number,
    :order-ref,
    :auto-start-token,
    :status,
    :hint-code,
    :http-status,
    :user,
    :device,
    :cert,
    :signature,
    :ocsp-response,
    :error-code,
    :details,
    :exception,
    :other);
