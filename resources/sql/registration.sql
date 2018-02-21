-- :name registration-allowed? :? :1
SELECT RegistrationPossible AS allowed
FROM c_treatmentinterface
WHERE ObjectId=:project-id;

