-- :name admin-reminder-unread-messages :? :*
-- :doc
SELECT
  cm.ParentId AS `user-id`,
  from_unixtime(min(SendTime)) AS `time`,
  count(ParentId) AS `count`
FROM c_message AS cm
JOIN links_c_message AS pl
  ON pl.PropertyName = 'Sender' AND cm.objectid = pl.LinkerId AND cm.parentid = pl.linkeeid
WHERE
  readtime = 0
  AND draft = 0
  AND cm.ParentProperty = 'Messages'
  AND SendTime > :time-limit
GROUP BY cm.ParentId;

-- :name admin-reminder-unread-homework :? :*
-- :doc
SELECT
  cta.`ParentId` AS `user-id`,
  from_unixtime(min(cdh.`Time`)) AS `time`,
  count(cta.ParentId) AS `count`
FROM content_data_homework AS cdh
JOIN c_treatmentaccess AS cta
  ON cdh.Id IN(SELECT Max(Id)
          FROM content_data_homework
          GROUP BY TreatmentAccessId, ModuleId)
  AND (cdh.InspectionTime = 0)
  AND cdh.TreatmentAccessId = cta.ObjectId
  AND cdh.`Time` > :time-limit
JOIN links_c_treatmentaccess AS lca
    ON cta.ObjectId = lca.LinkerId AND lca.PropertyName = "Treatment"
JOIN c_treatment AS ct
  ON lca.LinkeeId = ct.ObjectId
  AND ct.UseHomeworkInspection = 1
GROUP BY `user-id`;

-- :name admin-reminder-open-flags :? :*
-- :doc
SELECT
	ParentId AS `user-id`,
  from_unixtime(min(Created)) AS `time`,
  count(ParentId) AS `count`
FROM c_flag
WHERE
`ClosedAt` = 0
AND Created > :time-limit
GROUP BY ParentId;

-- :name admin-reminder-lost-password-flags :? :*
-- :doc
SELECT
	ParentId AS `user-id`,
  from_unixtime(min(Created)) AS `time`,
  count(ParentId) AS `count`
FROM c_flag
WHERE
	`ClosedAt` = 0
    AND `Issuer` = "lost-password"
    AND Created > :time-limit
GROUP BY ParentId;

-- :name admin-reminder-get-therapists :? :*
-- :doc
SELECT
  ct.ObjectId AS `therapist-id`,
  ct.email,
  lt.LinkeeId AS `participant-id`
FROM c_therapist as ct
  JOIN links_c_therapist lt
  ON (ct.ObjectId = lt.LinkerId
    AND lt.PropertyName = 'myparticipants')
WHERE ct.Enabled = 1 AND lt.LinkeeId IN(:v*:participant-ids) AND ct.Email LIKE "%@%";

-- :name admin-reminder-get-projects :? :*
-- :doc HOORAY, NO PARENT INTERFACE HERE (but sadly c_participantscollection)
SELECT
  cp.ObjectId AS `participant-id`,
  cti.ObjectId AS `project-id`
FROM c_participant AS cp
JOIN c_participantscollection cpc
  ON cp.ParentId = cpc.ObjectId
JOIN c_treatmentinterface AS cti
	ON cpc.ParentId = cti.ObjectId
WHERE cp.ObjectId IN(:v*:participant-ids);