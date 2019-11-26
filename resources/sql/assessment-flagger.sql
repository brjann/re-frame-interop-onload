
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
--     LATE ASSESSMENTS
-- -------------------------

-- :name get-flag-late-participant-administrations :? :*
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
  AND (ca.FlagParticipantWhenLate = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND cpa.Active = 1 AND (cga.Active = 1 OR cga.Active IS NULL)
  AND cpa.Date >= :oldest-allowed
  AND date_add(from_unixtime(cpa.`date`), INTERVAL ca.DayCountUntilLate DAY) <= :date;


-- :name XXXget-late-group-administrations :? :*
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
  AND (ca.SendSMSWhenActivated = 1 OR ca.SendEmailWhenActivated = 1)
  AND (ca.RemindParticipantsWhenLate = 1)
	AND (cpa.DateCompleted = 0 OR cpa.DateCompleted IS NULL)
	AND cga.Active = 1 AND (cpa.Active = 1 OR cpa.Active IS NULL)
	AND cga.Date >= 0
  AND date_add(from_unixtime(cga.`date`), INTERVAL ca.RemindInterval DAY) <= :date
	AND date_add(from_unixtime(cga.`date`), INTERVAL (ca.RemindInterval * ca.MaxRemindCount + 1) DAY) >= :date;

-- ----------------------------------
--   MASS ADMINISTRATIONS RETRIEVER
-- ----------------------------------

-- :name XXXget-participant-administrations-by-user+assessment :? :*
-- :doc
SELECT
	cpa.ParentId AS `user-id`,
  cpa.ObjectId AS `participant-administration-id`,
  ca.ObjectId AS `assessment-id`,
	cpa.AssessmentIndex AS `assessment-index`,
  cpa.Active AS `participant-administration-active?`,
  cpa.EmailSent AS `activation-reminder-sent?`,
  cpa.RemindersSent AS `late-reminders-sent`,

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
WHERE (cpa.ParentId, cpa.Assessment, ca.ParentId) IN (:t*:user-ids+assessment-ids);


-- :name XXXget-group-administrations-by-group+assessment :? :*
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
WHERE (cga.ParentId, cga.Assessment, ca.ParentId) IN (:t*:group-ids+assessment-ids);

-- :name XXXget-remind-assessments :? :*
-- :doc
SELECT
    ObjectId AS `assessment-id`,
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
    SendEmailWhenActivated AS `remind-by-email?`,
    SendSMSWhenActivated AS `remind-by-sms?`,
    RemindParticipantsWhenLate AS `late-remind?`,
    RemindInterval AS `late-remind-interval`,
    MaxRemindCount AS `late-remind-count`,
    CustomReminderMessage AS `remind-message`,
    ActivatedEmailSubject AS `activation-email-subject`,
    ReminderEmailSubject AS `late-email-subject`,
    CreateNewQuickLoginOnActivation AS `generate-quick-login?`,
    SortOrder AS `sort-order`
FROM c_assessment
WHERE ObjectId IN(:v*:assessment-ids);


-- :name XXXactivation-reminders-sent! :! :1
-- :doc
UPDATE c_participantadministration
SET EmailSent = 1
WHERE ObjectId IN (:v*:administration-ids);


-- :name XXXlate-reminders-sent! :! :1
-- :doc
INSERT INTO c_participantadministration
(ObjectId, RemindersSent)
VALUES :t*:remind-numbers
ON DUPLICATE KEY UPDATE
    RemindersSent = VALUES(RemindersSent);


-- :name XXXget-users-info :? :*
-- :doc
SELECT
  ObjectId as `user-id`,
  Email AS `email`,
  SMSNumber AS `sms-number`,
  QuickLoginPassword AS `quick-login-id`,
  QuickLoginTimestamp AS `quick-login-timestamp`,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email
FROM c_participant
WHERE ObjectId IN (:v*:user-ids);


-- :name XXXget-standard-messages :? :1
-- :doc
SELECT
 StandardSMSReminderMessage AS `sms`,
 StandardEmailReminderMessage AS `email`
FROM c_project WHERE ObjectId = 100;


-- :name XXXget-db-url :? :1
-- :doc
SELECT
 BASS4_URL AS `url`
FROM c_project WHERE ObjectId = 100;


-- :name XXXget-reminder-start-and-stop :? :1
-- :doc
SELECT
	AdministrationsRemindersStartHour AS `start-hour`,
	AdministrationsRemindersLastHour AS `stop-hour`
FROM c_project WHERE ObjectId = 100;