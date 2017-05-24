-- :name get-instrument-answers-by-administration :? :1
-- :doc
SELECT ObjectId AS `answers-id`
FROM c_instrumentanswers
WHERE
  ParentId = :administration-id AND
  Instrument = :instrument-id;

-- :name create-instrument-answers! :! :1
-- :doc Update created administration placeholders with assessmentindex some defaults
UPDATE c_instrumentanswers
SET
  ParentId = :administration-id,
  Instrument = :instrument-id
WHERE
  ObjectId = :answers-id;

-- :name delete-instrument-answers! :! :1
-- :doc
DELETE FROM c_instrumentanswers WHERE ObjectId = :answers-id;

-- :name save-instrument-answers! :! :n
-- :doc save instrument answers
UPDATE c_instrumentanswers
SET
  Items = :items,
  Specifications = :specifications,
  Sums = :sums,
  DateCompleted = unix_timestamp(UTC_TIMESTAMP()),
  Changed = unix_timestamp(UTC_TIMESTAMP())
--  DateCompleted = unix_timestamp(now()),
--  Changed = unix_timestamp(now())
WHERE ObjectId = :answers-id;