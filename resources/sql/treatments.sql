
-- :name get-linked-treatments :? :*
-- :doc get user's active treatments (NOTE: DOES NOT CHECK IF THEY ARE ONGOING)
SELECT
  ca.ObjectId    AS `treatment-access-id`,
  lca.LinkeeId   AS `treatment-id`,
  ModuleAccesses AS `module-accesses`
FROM c_treatmentaccess AS ca
  JOIN links_c_treatmentaccess AS lca
    ON ca.ObjectId = lca.LinkerId AND lca.PropertyName = "Treatment"
WHERE ca.ParentId = :user-id;

-- :name get-treatment-info :? :1
-- :doc
SELECT
  ObjectId AS 'treatment-id',
  `Name`
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
  ctc.`Name`       AS `content-name`,
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
  IsMarkDown AS `markdown`,
  DataName   AS `data-name`,
  ImportData AS `data-imports`
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
