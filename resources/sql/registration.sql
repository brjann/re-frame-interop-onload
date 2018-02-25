-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
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
  RegistrationSMSCountries AS `sms-countries`,
  RegistrationInfo AS info,
  RegistrationIsMarkdown AS `markdown?`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-params :? :1
SELECT
  RegistrationFields AS `fields`,
  DefaultRegistrationGroup AS `group`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;
