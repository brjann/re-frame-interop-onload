
-- :name get-user-group :? :1
-- :doc Get the group that a user belongs to
SELECT
    cg.Name as `group-name`,
    cg.ObjectId as `group-id`
FROM c_participant AS cp
    LEFT JOIN c_group AS cg
        ON (cp.`Group` = cg.ObjectId)
WHERE cp.ObjectId = :user-id


-- :name get-assessment-series-assessments :? :*
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
    IsRecord AS `is-record`,
    ClinicianAssessment AS `clinician-rated`,
    CompetingAssessmentsPriority AS `priority`,
    CompetingAssessmentsAllowSwallow AS `allow-swallow`,
    CompetingAssessmentsShowTextsIfSwallowed AS `show-texts-if-swallowed`,
    WelcomeText AS `welcome-text`,
    ThankYouText AS `thank-you-text`,
   ShuffleInstruments AS `shuffle-instruments`

FROM c_assessment
WHERE parentid = :assessment-series-id

-- :name get-user-assessment-series :? :1
-- :doc Get the assessment series that the user's project uses
SELECT cti.Assessments as `assessment-series-id` FROM c_participant AS cp
    JOIN c_treatmentinterface AS cti
        ON cp.ParentInterface = cti.ObjectId
WHERE cp.ObjectId = :user-id

-- :name get-user-administrations :? :*
-- :doc Gets the participant and group administrations for a user.
--  NOTE that ghost administrations are included and need to be filtered out.
--  Always select the administration with the highest combination of
--  participant_administration_id and group_administration_id
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
     END) AS `active`,

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
    (ca.ParentId = :assessment-series-id OR ca.ParentId = :user-id) ORDER BY ca.ObjectId, cga.AssessmentIndex, cpa.ObjectId, cga.ObjectId;

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
Deleted = VALUES(Deleted)


-- :name update-new-participant-administration! :! :1
-- :doc Update created administration placeholders with assessmentindex and assessment some defaults
UPDATE c_participantadministration
SET
    ParentId = :user-id,
    Assessment = :assessment-id,
    AssessmentIndex = :assessment-index,
Active = 1,
Deleted = 0
WHERE ObjectId = :administration-id


-- :name delete-participant-administration! :! :1
-- :doc Update an object with new parent in objectlist
DELETE FROM c_participantadministration WHERE ObjectId = :administration-id


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


-- :name lock-assessment-rounds-table! :! :n
-- :doc Lock the table for writing
LOCK TABLES assessment_rounds WRITE;

-- :name get-new-round-id :? :1
-- :doc Get the max new round id
SELECT
  max(RoundId) + 1 AS `round-id`
FROM assessment_rounds;

-- :name insert-assessment-round! :! :n
-- :doc Tuple Param List
INSERT INTO assessment_rounds (RoundId, Time, UserId, BatchId, Step, Text, InstrumentId, AdministrationId)
VALUES :t*:rows;

-- :name unlock-tables! :! :n
UNLOCK TABLES;