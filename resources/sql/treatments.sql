
-- :name get-treatment-accesses :? :*
-- :doc get user's treatment accesses (NOTE: DOES NOT CHECK IF THEY ARE ONGOING)
SELECT
  ca.ObjectId    AS `treatment-access-id`,
  lca.LinkeeId   AS `treatment-id`,
  ModuleAccesses AS `module-accesses`,
  AccessEnabled  AS `access-enabled`,
	from_unixtime(StartDate)    AS `start-date`,
	from_unixtime(EndDate)      AS `end-date`,
	1 - MessagesSendDisallow    AS `messages-send-allowed`,
  1 - MessagesReceiveDisallow AS `messages-receive-allowed`
FROM c_treatmentaccess AS ca
  JOIN links_c_treatmentaccess AS lca
    ON ca.ObjectId = lca.LinkerId AND lca.PropertyName = "Treatment"
WHERE ca.ParentId = :user-id ORDER BY ca.ObjectId;

-- :name get-treatment-info :? :1
-- :doc
SELECT
  ObjectId AS `treatment-id`,
  `Name` AS `name`,
  `AccessStartAndEndDate` AS `access-time-limited`,
  `AccessEnablingRequired` AS `access-enabling-required`,
  `ModulesManualAccess` AS `modules-manual-access`,
  `ModuleAutomaticAccess` AS `modules-automatic-access`,
  `MessagingAllowParticipant` AS `messages-send-allowed`,
  `MessagingAllowTherapist` AS  `messages-receive-allowed`
FROM c_treatment AS ct
WHERE ct.ObjectId=:treatment-id;

-- :name get-treatment-modules :? :*
-- :doc treatment modules of treatment
SELECT
  cm.ObjectId AS `module-id`,
  cm.Name AS `module-name`
FROM links_c_treatment AS lct
  JOIN c_module AS cm
    ON lct.LinkeeId = cm.ObjectId
WHERE lct.LinkerId=:treatment-id AND lct.PropertyName="Modules"
ORDER BY lct.SortOrder;

-- :name get-module-contents :? :*
-- :doc get worksheets belonging to module-ids
SELECT DISTINCT
  cm.ObjectId      AS `module-id`,
  ctc.ObjectId     AS `content-id`,
  ctc.DataName     AS `data-name`,
  ctc.`Name`       AS `content-name`,
  ctc.FilePath     AS `file-path`,
  (CASE
    WHEN (ctc.Text IS NULL OR ctc.Text = "") THEN
      FALSE
    ELSE
      TRUE
  END) AS `has-text?`,

  lcm.PropertyName AS `type`
FROM c_module as cm
  JOIN links_c_module AS lcm
    ON cm.ObjectId = lcm.LinkerId
  JOIN c_treatmentcontent AS ctc
    ON lcm.LinkeeId = ctc.ObjectId
WHERE lcm.LinkerId IN (:v*:module-ids)
ORDER BY lcm.SortOrder, cm.SortOrder;

-- :name get-content :? :1
-- :doc
SELECT
  Text,
  IsMarkDown  AS `markdown`,
  ShowExample AS `show-example`,
  Tabbed      AS `tabbed`,
  DataName    AS `data-name`,
  `Name`      AS `content-name`,
  ImportData  AS `data-imports`,
  FilePath    AS `file-path`
FROM c_treatmentcontent
WHERE ObjectId=:content-id;

-- :name get-content-data :? :*
-- :doc get the content data
SELECT *
FROM content_data
WHERE Id IN (SELECT MAX(Id)
             FROM content_data
             WHERE DataOwnerId=:data-owner-id AND DataName IN (:v*:data-names)
             GROUP BY DataName, ValueName);


-- :name get-content-data-last-save :? :*
-- :doc get the content data
SELECT
	DataName AS `data-name`,
    from_unixtime(Time) AS `time`
FROM content_data
WHERE Id IN (SELECT MAX(Id)
             FROM content_data
             WHERE DataOwnerId= :data-owner-id
             GROUP BY DataName);

-- :name save-content-data! :! :n
-- :doc save content data
INSERT INTO content_data
(DataOwnerId, `Time`, DataName, ValueName, `Value`)
VALUES :t*:content-data;


-- :name submit-homework! :! :n
-- :doc
REPLACE INTO content_data_homework
(TreatmentAccessId, ModuleId, `Time`, InspectorId, InspectionTime, OK)
VALUES (:treatment-access-id, :module-id, unix_timestamp(now()), 0, 0, 0);

-- :name get-submitted-homeworks :? :*
-- :doc get the content data
SELECT
  Id AS `submit-id`,
  ModuleId AS `module-id`,
  from_unixtime(`Time`) AS `Time`,
  OK AS `ok?`
FROM content_data_homework
WHERE Id IN
  (SELECT max(Id) FROM
    content_data_homework
  WHERE
    TreatmentAccessId = :treatment-access-id
  GROUP BY ModuleId);

-- :name retract-homework! :! :1
-- :doc
DELETE FROM content_data_homework WHERE Id = :submit-id;


-- :name register-content-access! :! :n
-- :doc
INSERT IGNORE INTO content_data_accesses
(content_id, treatment_access_id, module_id, `time`)
VALUES (:content-id, :treatment-access-id, :module-id, now());

-- :name get-content-first-access :? :*
-- :doc
SELECT
	module_id AS `module-id`,
	content_id AS `content-id`,
    `time`
FROM content_data_accesses
WHERE treatment_access_id = :treatment-access-id AND module_id IN(:v*:module-ids) ;