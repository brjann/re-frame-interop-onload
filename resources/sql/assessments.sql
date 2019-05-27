
-- :name get-user-group :? :1
-- :doc Get the group that a user belongs to
SELECT
    cg.Name as `group-name`,
    cg.ObjectId as `group-id`
FROM c_participant AS cp
    LEFT JOIN c_group AS cg
        ON (cp.`Group` = cg.ObjectId)
WHERE cp.ObjectId = :user-id;


-- :name get-user-assessments :? :*
-- :doc Get the assessments of an assessment series
SELECT
    ObjectId AS `assessment-id`,
    Name AS `assessment-name`,
    scope,
    (CASE
     WHEN RepetitionType = 2 OR Type = "PUNKT"
         THEN "MANUAL"
     ELSE
         (CASE
          WHEN RepetitionType = 0 AND Type <> "WEEK"
              THEN "NONE"
          ELSE "INTERVAL"
          END)
     END) AS `repetition-type`,
    (CASE
     WHEN repetitions = 0
         THEN 1
     ELSE
         (CASE
          WHEN (RepetitionType = 0 AND Type <> "WEEK" AND Type <> "CUSTOM")
              THEN 1
          ELSE repetitions
          END)
     END) AS repetitions,
    TimeLimit AS `time-limit`,
    (CASE
     WHEN ActivationHour > 23
         THEN 0
     ELSE ActivationHour
     END ) AS `activation-hour`,
    (CASE
     WHEN RepetitionType = 1 OR Type = "WEEK"
         THEN 7
     ELSE CustomRepetitionInterval
     END) AS `repetition-interval`,
    IsRecord AS `is-record?`,
    ClinicianAssessment AS `clinician-rated?`,
    CompetingAssessmentsPriority AS `priority`,
    CompetingAssessmentsAllowSwallow AS `allow-swallow?`,
    CompetingAssessmentsShowTextsIfSwallowed AS `show-texts-if-swallowed?`,
    WelcomeText AS `welcome-text`,
    ThankYouText AS `thank-you-text`,
   ShuffleInstruments AS `shuffle-instruments`

FROM c_assessment
WHERE parentid = :assessment-series-id OR parentid = :user-id;

-- :name get-user-assessment-series :? :*
-- :doc Get the assessment series that the user's project uses
SELECT
  cp.ObjectId as `user-id`,
  cti.Assessments as `assessment-series-id`
FROM c_participant AS cp
    JOIN c_treatmentinterface AS cti
        ON cp.ParentInterface = cti.ObjectId
WHERE cp.ObjectId IN (:v*:user-ids);

-- :name get-user-administrations :? :*
-- :doc Gets the participant and group administrations for a user.
SELECT
    ca.Name AS `assessment-name`,
    ca.ObjectId AS `assessment-id`,
    cpa.ObjectId AS `participant-administration-id`,

  (CASE
     WHEN cpa.AssessmentIndex IS NULL
         THEN cga.AssessmentIndex
     ELSE cpa.AssessmentIndex
     END) AS `assessment-index`,

  (CASE
     WHEN cpa.Active IS NULL AND cga.Active IS NULL
         THEN NULL
     ELSE
         (CASE
          WHEN cpa.Active = 0 OR cga.Active = 0
              THEN 0
          ELSE 1
          END)
     END) AS `active?`,

  (CASE
     WHEN cpa.DateCompleted IS NULL
         THEN 0
     ELSE cpa.DateCompleted
     END ) AS `date-completed`,

  (CASE
   WHEN cpa.`Date` IS NULL OR cpa.`Date` = 0
     THEN
       NULL
   ELSE
     from_unixtime(cpa.`Date`)
   END) AS `participant-activation-date`,

  cga.ObjectId AS `group-administration-id`,

  (CASE
   WHEN cga.Date IS NULL OR cga.Date = 0
     THEN
       NULL
   ELSE
     from_unixtime(cga.Date)
   END) AS `group-activation-date`

FROM c_assessment as ca
    LEFT JOIN (c_participantadministration as cpa)
        ON (ca.ObjectId = cpa.Assessment AND cpa.ParentId = :user-id AND cpa.Deleted = 0)
    LEFT JOIN (c_groupadministration AS cga)
        ON (ca.ObjectId = cga.Assessment AND cga.ParentId = :group-id
            AND (cpa.AssessmentIndex IS NULL OR cpa.AssessmentIndex = cga.AssessmentIndex))
WHERE
    (ca.ParentId = :assessment-series-id OR ca.ParentId = :user-id);


-- :name create-participant-administrations! :! :1
-- :doc Update created administration placeholders with assessmentindex some defaults
INSERT INTO c_participantadministration
(ObjectId, ParentId, Assessment, AssessmentIndex, Active, Deleted)
VALUES :t*:administrations-values
ON DUPLICATE KEY UPDATE
    ParentId = VALUES(ParentId),
Assessment = VALUES(Assessment),
AssessmentIndex = VALUES(AssessmentIndex),
Active = VALUES(Active),
Deleted = VALUES(Deleted);


-- :name update-new-participant-administration! :! :1
-- :doc Update created administration placeholders with assessmentindex and assessment some defaults
UPDATE c_participantadministration
SET
    ParentId = :user-id,
    Assessment = :assessment-id,
    AssessmentIndex = :assessment-index,
Active = 1,
Deleted = 0
WHERE ObjectId = :administration-id;


-- :name delete-participant-administration! :! :1
-- :doc
DELETE FROM c_participantadministration WHERE ObjectId = :administration-id;


-- :name get-administration-by-assessment-and-index :? :1
-- :doc Get participant administration by assessment and index for user
SELECT ObjectId AS `administration-id`
FROM c_participantadministration
WHERE
    ParentId = :user-id AND
    Assessment = :assessment-id AND
    AssessmentIndex = :assessment-index



-- :name get-assessment-instruments :? :*
-- :doc Get assessment's instrument
SELECT ObjectId AS `instrument-id`
FROM c_instrument AS ci
    JOIN
    links_c_assessment AS lca
        ON ci.ObjectId = lca.LinkeeId AND lca.PropertyName = "Instruments"
WHERE
    lca.LinkerId = :assessment-id
ORDER BY
    lca.SortOrder;

-- :name get-assessments-instruments :? :*
-- :doc Get assessment's instrument - EXCEPT clinician assessed
SELECT
  ObjectId AS `instrument-id`,
  lca.LinkerId AS `assessment-id`
FROM c_instrument AS ci
  JOIN
  links_c_assessment AS lca
    ON ci.ObjectId = lca.LinkeeId AND lca.PropertyName = "Instruments"
WHERE
  lca.LinkerId IN(:v*:assessment-ids) AND (ci.ClinicianAssessed = 0 OR ci.ClinicianAssessed IS NULL)
ORDER BY
  lca.SortOrder;


-- :name get-administration-additional-instruments :? :*
-- :doc EXCEPT clinician assessed
SELECT
  LinkeeId AS `instrument-id`,
  LinkerId as `administration-id`
FROM links_c_participantadministration AS lcp
  JOIN c_instrument AS ci
    ON linkeeid = objectid
WHERE linkerid IN(:v*:administration-ids)
      AND propertyname = "AdditionalInstruments"
      AND (ci.ClinicianAssessed = 0 OR ci.ClinicianAssessed IS NULL)
ORDER BY lcp.SortOrder;

-- :name insert-assessment-round! :! :n
-- :doc insert assessment rounds from tuple param list
INSERT INTO assessment_rounds (RoundId, Time, UserId, BatchId, Step, Texts, InstrumentId, AdministrationId)
VALUES :t*:rows;

-- :name get-current-assessment-round :? :*
-- :doc
SELECT
  id,
  userid as `user-id`,
  roundid as `round-id`,
  administrationid as `administration-id`,
  batchid as `batch-id`,
  (CASE
   WHEN completed IS NULL OR completed = 0
     THEN NULL
   ELSE
     1
   END) AS `completed`,
  (CASE
   WHEN instrumentid IS NULL OR instrumentid = 0
     THEN NULL
   ELSE
     instrumentid
   END) AS `instrument-id`,
  texts,
  (CASE
   WHEN mustshowtexts IS NULL OR mustshowtexts = 0
     THEN NULL
   ELSE
     mustshowtexts
   END) AS `must-show-texts`,
  step
FROM assessment_rounds
WHERE
--  (Completed = 0 OR Completed IS NULL)
--  AND
  RoundId = (SELECT max(RoundId) FROM assessment_rounds WHERE UserId = :user-id)
ORDER BY step;


-- :name set-step-completed! :! :n
-- :doc
UPDATE assessment_rounds SET Completed = UTC_TIMESTAMP() WHERE Id = :id;


-- :name set-instrument-completed! :! :n
-- :doc
UPDATE assessment_rounds SET Completed = UTC_TIMESTAMP()
WHERE
  (InstrumentId = :instrument-id)
  AND
  RoundId = (SELECT max(RoundId) FROM (SELECT * FROM assessment_rounds) AS xxx WHERE UserId = :user-id);

-- :name set-batch-must-show-texts! :! :n
-- :doc
UPDATE assessment_rounds SET MustShowTexts = 1
WHERE
  (BatchId = :batch-id)
  AND
  (RoundId = :round-id)
  AND
  (Texts != "");

-- :name get-administration-completed-instruments :? :*
SELECT
  ParentId AS `administration-id`,
  Instrument AS `instrument-id`
  FROM c_instrumentanswers
WHERE
ParentId IN(:v*:administration-ids) AND DateCompleted > 0;

-- :name set-administration-complete! :! :n
-- :doc
UPDATE c_participantadministration
  SET DateCompleted = unix_timestamp(now())
WHERE ObjectId IN(:v*:administration-ids);

-- :name set-last-assessment! :! :n
-- :doc
UPDATE  c_participantadministration as cpa
  JOIN c_assessment AS ca ON cpa.Assessment = ca.ObjectId
  JOIN c_participant AS cp ON cpa.parentid = cp.objectid
SET cp.lastassessment = ca.objectid,
    cp.LastAssessmentIndex = cpa.assessmentindex
WHERE cpa.ObjectId = :administration-id;

-- :name get-last-assessment :? :1
-- :doc
SELECT
  LastAssessment AS `assessment-id`,
  LastAssessmentIndex `assessment-index`
FROm c_participant
WHERE ObjectId = :user-id;

-- :name get-last-assessment :? :1
-- :doc
SELECT
  LastAssessment AS `assessment-id`,
  LastAssessmentIndex `assessment-index`
FROm c_participant
WHERE ObjectId = :user-id;

-- :name get-completed-answers :? :*
-- :doc used for testing
SELECT * FROM
  c_participantadministration AS cpa
  JOIN c_instrumentanswers AS cia
  ON cpa.ObjectId = cia.ParentId
WHERE
  cpa.ParentId = :user-id AND cia.DateCompleted > 0;


-- :name get-assessments-with-date :? :*
-- :doc used for testing
SELECT Assessment AS `assessment-id` FROM
  c_participantadministration
WHERE
  parentid = :user-id AND (DateCompleted = 0 OR DateCompleted IS NULL) AND `Date` > 0;

-- :name get-dependent-assessments :? :*
SELECT
  ca2.ObjectId AS `assessment-id`,
  ca2.OffsetDays AS `offset-days`
FROM
  c_participantadministration AS cpa
  JOIN
  c_assessment AS ca1
    ON (cpa.Assessment = ca1.ObjectId)
  JOIN
  c_assessment AS ca2
    ON (ca2.OffsetAssessment = ca1.ObjectId AND ca2.OffsetDynamic = 1)
WHERE
  cpa.ObjectId IN(:v*:administration-ids);


-- :name set-administration-dates! :! :1
-- :doc
INSERT INTO c_participantadministration
(ObjectId, Date)
VALUES :t*:dates
ON DUPLICATE KEY UPDATE
  ObjectId = VALUES(ObjectId),
  Date = VALUES(Date);


-- ----------------------------------
-- ------ ASSESSMENTS REMINDER ------
-- ----------------------------------

-- :name get-activated-participant-administrations :? :*
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
  cpa.AssessmentIndex AS `administration-index`
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
  AND ca.ActivationHour >= :hour
  AND (ca.SendSMSWhenActivated = 1 OR ca.SendEmailWhenActivated = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND (cpa.EmailSent = 0 OR cpa.EmailSent IS NULL)
	AND cpa.Active = 1 AND (cga.Active = 1 OR cga.Active IS NULL)
  AND cpa.Date >= :date-min AND cpa.Date <= :date-max;

-- :name get-activated-group-administrations :? :*
-- :doc

SELECT
	cp.ObjectId AS `user-id`,
  `Group` AS `group-id`,
  cpa.ObjectId AS `participant-administration-id`,
  cga.ObjectId AS `group-administration-id`,
	cga.Assessment AS `assessment-id`,
  cga.AssessmentIndex AS `administration-index`
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
  AND ca.ActivationHour >= :hour
  AND (ca.SendSMSWhenActivated = 1 OR ca.SendEmailWhenActivated = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND (cpa.EmailSent = 0 OR cpa.EmailSent IS NULL)
	AND cga.Active = 1 AND (cpa.Active = 1 OR cpa.Active IS NULL)
	AND cga.Date >= :date-min AND cga.Date <= :date-max;

-- :name get-participant-administrations-by-user+assessment :? :*
-- :doc

SELECT
	cpa.ParentId AS `user-id`,
  cpa.ObjectId AS `participant-administration-id`,
  ca.ObjectId AS `assessment-id`,
	cpa.AssessmentIndex AS `assessment-index`,
  cpa.Active AS `participant-administration-active?`,

  (CASE
    WHEN cpa.DateCompleted IS NULL
      THEN 0
    ELSE cpa.DateCompleted
  END ) AS `date-completed`,

  (CASE
    WHEN cpa.`Date` IS NULL OR cpa.`Date` = 0
      THEN NULL
    ELSE from_unixtime(cpa.`Date`)
  END) AS `participant-activation-date`

FROM c_participantadministration as cpa
    JOIN c_assessment AS ca
        ON (ca.ObjectId = cpa.Assessment AND cpa.Deleted = 0)
WHERE (cpa.ParentId, cpa.Assessment) IN (:t*:user-ids+assessment-ids)

-- :name get-group-administrations-by-group+assessment :? :*
-- :doc

SELECT
	cga.ParentId AS `group-id`,
  cga.ObjectId AS `group-administration-id`,
  ca.ObjectId AS `assessment-id`,
	cga.AssessmentIndex AS `assessment-index`,
  cga.Active AS `group-administration-active?`,
  (CASE
    WHEN cga.`Date` IS NULL OR cga.`Date` = 0
      THEN NULL
    ELSE from_unixtime(cga.`Date`)
  END) AS `group-activation-date`

FROM c_groupadministration as cga
    JOIN c_assessment AS ca
        ON (ca.ObjectId = cga.Assessment)
WHERE (cga.ParentId, cga.Assessment) IN (:t*:group-ids+assessment-ids)