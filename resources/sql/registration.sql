-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name captcha-content :? :1
SELECT RegistrationCaptcha AS `captcha-content`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-content :? :1
SELECT
  RegistrationFields AS `fields`,
  RegistrationPIDValidator AS `pid-validator`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-params :? :1
SELECT
  RegistrationFields AS `fields`,
  DefaultRegistrationGroup AS `group`,
  RegistrationPIDName AS `pid-name`,
  RegistrationPIDFOrmat AS `pid-format`,
  RegistrationSMSCountries AS `sms-countries`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;
