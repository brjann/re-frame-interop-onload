-- :name create-bass-object! :? :1
-- :doc Create a new BASS object and return it's ObjectId
CALL create_bass_objects(:class-name, :parent-id, :property-name, 1);

-- :name create-bass-objects-without-parent! :? :1
-- :doc Create many new BASS objects and return the latest's ObjectId
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

-- :name get-db-title :? :1
SELECT title
FROM c_project
WHERE ObjectId=100;

-- :name get-sms-sender :? :1
SELECT SMSSender AS `sms-sender`
FROM c_project
WHERE ObjectId=100;

-- :name get-contact-info :? :1
SELECT cp.Email AS `db-email`, ct.DefaultEmailRecipient AS `project-email`
FROM c_project AS cp LEFT JOIN c_treatmentinterface AS ct ON ct.ObjectId = :project-id
WHERE cp.ObjectId=100;

-- :name ext-login-settings :? :1
SELECT ExternalLoginAllowed AS allowed,
  ExternalLoginIps AS ips
FROM c_project
WHERE ObjectId=100;


-- :name link-property-reverse! :! :1
-- :doc
INSERT IGNORE INTO links_properties_reverse
(LinkeeId, PropertyName, LinkerClass) VALUES (:linkee-id, :property-name, :linker-class);

-- :name get-support-email :? :1
SELECT (CASE
        WHEN ct.DefaultEmailRecipient="" OR ct.DefaultEmailRecipient IS NULL
          THEN cp.Email
        ELSE ct.DefaultEmailRecipient
        END) AS email
FROM c_treatmentinterface AS ct
  JOIN c_project AS cp ON cp.ObjectId=100
WHERE ct.ObjectId=:project-id;

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


-- :name increase-sms-count! :! :1
-- :doc
INSERT INTO sms_count (`day`, `count`)
				VALUES (:day, 1)
				ON DUPLICATE KEY UPDATE `count` = `count` + 1;