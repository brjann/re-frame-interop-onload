-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name finished-content :? :1
SELECT
  RegistrationFinished AS `content`,
  RegistrationIsMarkdown AS `markdown?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name captcha-content :? :1
SELECT
  RegistrationCaptcha AS `content`,
  RegistrationIsMarkdown AS `markdown?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-content :? :1
SELECT
  RegistrationFields AS `fields`,
  RegistrationPIDValidator AS `pid-validator`,
  RegistrationPIDName AS `pid-name`,
  RegistrationPIDFOrmat AS `pid-format`,
  RegistrationSMSCountries AS `sms-countries`,
  RegistrationInfo AS info,
  RegistrationIsMarkdown AS `markdown?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-params :? :1
SELECT
  RegistrationFields AS `fields`,
  DefaultRegistrationGroup AS `group`,
  RegistrationSMSCountries AS `sms-countries`,
  RegistrationAutoUsername AS `auto-username`,
  RegistrationAllowDuplicateEmail AS `allow-duplicate-email?`,
  RegistrationAllowDuplicateSMS AS `allow-duplicate-sms?`,
  RegistrationAutoId AS `auto-id?`,
  RegistrationAutoIdPrefix AS `auto-id-prefix`,
  RegistrationAutoIdLength AS `auto-id-length`,
  RegistrationAutoPassword AS `auto-password?`,
  RegistrationUseBankID AS `bankid?`,
  RegistrationAllowChangeBankIDNames AS `bankid-change-names?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name check-duplicate-info :? :1
SELECT
  count(*) AS `count`
FROM c_participant
WHERE
  SMSNumber = :sms-number
  OR
  Email = :email;

-- :name get-current-auto-id-for-update :? :1
SELECT RegistrationAutoIdCurrent as `auto-id`
FROM c_treatmentinterface
WHERE ObjectId=:project-id
FOR UPDATE;

-- :name increment-auto-id! :! :1
UPDATE c_treatmentinterface
SET RegistrationAutoIdCurrent = RegistrationAutoIdCurrent + 1
WHERE ObjectId=:project-id;

-- :name check-participant-id :? :1
SELECT
  count(*) AS `count`
FROM c_participant
WHERE
  ParticipantId = :participant-id;

-- :name registration-show-finished :? :1
SELECT
  RegistrationAlwaysShowFinishedScreen AS `show-finished-screen?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;