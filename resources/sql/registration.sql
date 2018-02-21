-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

-- :name captcha-content :? :1
SELECT RegistrationCaptcha AS `captcha-content`
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

