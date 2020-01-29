
-- ----------------------------------
-- ----------------------------------
-- ------ ASSESSMENTS REMINDER ------
-- ----------------------------------
-- ----------------------------------


-- -------------------------
--   ACTIVATED ASSESSMENTS
-- -------------------------

-- :name XXXget-activated-participant-administrations :? :*
-- :doc
SELECT
	cp.ObjectId AS `user-id`,
 (CASE
   WHEN `Group` IS NULL OR `Group` = 0
     THEN NULL
   ELSE
     `Group`
   END) AS `group-id`,
  cpa.ObjectId AS `participant-administration-id`,
  cga.ObjectId AS `group-administration-id`,
	cpa.Assessment AS `assessment-id`,
  cpa.AssessmentIndex AS `assessment-index`
FROM
	c_participant AS cp
    JOIN c_participantadministration AS cpa
		ON cp.ObjectId = cpa.Parentid
	LEFT JOIN c_groupadministration as cga
		ON
			cp.Group = cga.ParentId AND
			((cpa.Assessment = cga.Assessment AND
			cpa.AssessmentIndex = cga.AssessmentIndex))
	JOIN c_assessment AS ca
		ON cpa.Assessment = ca.ObjectId
WHERE
	ca.Scope = 0
  AND ca.ActivationHour <= :hour
  AND (ca.SendSMSWhenActivated = 1 OR ca.SendEmailWhenActivated = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND (cpa.EmailSent = 0 OR cpa.EmailSent IS NULL)
	AND cpa.Active = 1 AND (cga.Active = 1 OR cga.Active IS NULL)
  AND cpa.Date >= :date-min AND cpa.Date <= :date-max;


-- :name XXXget-activated-group-administrations :? :*
-- :doc
SELECT
	cp.ObjectId AS `user-id`,
  cp.`Group` AS `group-id`,
  cpa.ObjectId AS `participant-administration-id`,
  cga.ObjectId AS `group-administration-id`,
	cga.Assessment AS `assessment-id`,
  cga.AssessmentIndex AS `assessment-index`
FROM
	c_participant AS cp
  JOIN c_groupadministration as cga
		ON cp.Group = cga.ParentId
  LEFT JOIN c_participantadministration AS cpa
		ON cp.ObjectId = cpa.Parentid AND
			cpa.Assessment = cga.Assessment AND
			cpa.AssessmentIndex = cga.AssessmentIndex
	JOIN c_assessment AS ca
		ON cga.Assessment = ca.ObjectId
WHERE
	ca.Scope = 1
  AND ca.ActivationHour <= :hour
  AND (ca.SendSMSWhenActivated = 1 OR ca.SendEmailWhenActivated = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND (cpa.EmailSent = 0 OR cpa.EmailSent IS NULL)
	AND cga.Active = 1 AND (cpa.Active = 1 OR cpa.Active IS NULL)
	AND cga.Date >= :date-min AND cga.Date <= :date-max;


-- -------------------------
--   LATE FLAG ASSESSMENTS
-- -------------------------

-- :name get-late-flag-participant-administrations :? :*
-- :doc
SELECT
	cp.ObjectId AS `user-id`,
 (CASE
   WHEN `Group` IS NULL OR `Group` = 0
     THEN NULL
   ELSE
     `Group`
   END) AS `group-id`,
  cpa.ObjectId AS `participant-administration-id`,
  cga.ObjectId AS `group-administration-id`,
	cpa.Assessment AS `assessment-id`,
  cpa.AssessmentIndex AS `assessment-index`,
  cf.ObjectId AS `flag-id`
FROM
	c_participant AS cp
    JOIN c_participantadministration AS cpa
		ON cp.ObjectId = cpa.Parentid
	LEFT JOIN c_groupadministration as cga
		ON
			cp.Group = cga.ParentId AND
			((cpa.Assessment = cga.Assessment AND
			cpa.AssessmentIndex = cga.AssessmentIndex))
	JOIN c_assessment AS ca
		ON cpa.Assessment = ca.ObjectId
	LEFT JOIN c_flag AS cf
	  ON cf.ObjectId =
	    (SELECT ObjectId
	    FROM c_flag AS cfx
	    WHERE (cpa.ObjectId = cfx.ReferenceId AND cfx.Issuer = :issuer)
	    ORDER BY cfx.ClosedAt > 0, cfx.ClosedAt DESC LIMIT 1)
WHERE
	ca.Scope = 0
  AND (ca.FlagParticipantWhenLate = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND cpa.Active = 1 AND (cga.Active = 1 OR cga.Active IS NULL)
  AND from_unixtime(cpa.Date) >= :oldest-allowed
  AND date_add(from_unixtime(cpa.`date`), INTERVAL ca.DayCountUntilLate DAY) <= :date
  AND
    (cf.ObjectId IS NULL
    OR
      (cf.ReflagPossible = 1
      AND cf.ClosedAt > 0
      AND date_add(from_unixtime(cf.ClosedAt), INTERVAL cf.ReflagDelay DAY) <= :date));


-- :name get-late-flag-group-administrations :? :*
-- :doc
SELECT
	cp.ObjectId AS `user-id`,
  cp.`Group` AS `group-id`,
  cpa.ObjectId AS `participant-administration-id`,
  cga.ObjectId AS `group-administration-id`,
	cga.Assessment AS `assessment-id`,
  cga.AssessmentIndex AS `assessment-index`,
  cf.ObjectId AS `flag-id`
FROM
	c_participant AS cp
  JOIN c_groupadministration as cga
		ON cp.Group = cga.ParentId
  LEFT JOIN c_participantadministration AS cpa
		ON cp.ObjectId = cpa.Parentid AND
			cpa.Assessment = cga.Assessment AND
			cpa.AssessmentIndex = cga.AssessmentIndex
	JOIN c_assessment AS ca
		ON cga.Assessment = ca.ObjectId
	LEFT JOIN c_flag AS cf
	  ON cf.ObjectId =
	    (SELECT ObjectId
	    FROM c_flag AS cfx
	    WHERE (cpa.ObjectId = cfx.ReferenceId AND cfx.Issuer = :issuer)
	    ORDER BY cfx.ClosedAt > 0, cfx.ClosedAt DESC LIMIT 1)
WHERE
	ca.Scope = 1
  AND (ca.FlagParticipantWhenLate = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND cga.Active = 1 AND (cpa.Active = 1 OR cpa.Active IS NULL)
  AND from_unixtime(cga.Date) >= :oldest-allowed
  AND date_add(from_unixtime(cga.`date`), INTERVAL ca.DayCountUntilLate DAY) <= :date
  AND
    (cf.ObjectId IS NULL
    OR
      (cf.ReflagPossible = 1
      AND cf.ClosedAt > 0
      AND date_add(from_unixtime(cf.ClosedAt), INTERVAL cf.ReflagDelay DAY) <= :date));


-- :name reopen-flags! :! :n
-- :doc
INSERT INTO c_flag
(`ObjectId`, `FlagText`)
VALUES :t*:flag-texts
ON DUPLICATE KEY UPDATE
  `Open` = 1,
  `ClosedAt` = 0,
  `FlagText` = VALUES(`FlagText`);


-- :name reopen-flag-comments! :! :n
-- :doc
INSERT INTO c_comment
(`ObjectId`, `ParentId`)
VALUES :t*:comment-parents
ON DUPLICATE KEY UPDATE
  `Text` = :comment-text,
  `ParentId` = VALUES(`ParentId`);


-- :name close-administration-late-flags! :! :n
-- :doc
UPDATE c_flag SET
  ClosedAt = unix_timestamp(:now),
  Open = 0,
  ReflagPossible = 0
WHERE Issuer = :issuer AND ReferenceId IN(:v*:administration-ids)