-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name captcha-content :? :1
SELECT RegistrationCaptcha AS `captcha-content`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-content :? :1
SELECT RegistrationForm AS `registration-content`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name registration-params :? :1
SELECT
  RegistrationFieldsMapping AS `fields-mapping`,
  DefaultRegistrationGroup AS `group`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;
