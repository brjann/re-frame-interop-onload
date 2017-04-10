
-- :name get-instrument :? :1
-- :doc get info about instrument
SELECT
  FullName AS name,
  ShowName AS `show-name`,
  Abbreviation
FROM c_instrument
WHERE ObjectId = :instrument-id


-- :name get-instrument-statics :? :*
-- :doc get the content data
SELECT
  text,
  SortOrder AS `sort-order`
FROM c_instrumentstaticelement
WHERE ParentId = :instrument-id


-- :name get-instrument-items :? :*
SELECT
  ObjectId AS `item-id`,
  name,
  text,
  CASE
    WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0)
      THEN OptionValues
    ELSE NULL
  END
    AS `option-values`,
  CASE
    WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0)
      THEN OptionLabels
    ELSE NULL
  END
    AS `option-labels`,
  ResponseType AS `response-type`,
  CASE
    WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0)
          THEN ObjectId
    ELSE ResponseDefElement
  END
    AS `response-id`,
  SortOrder AS `sort-order`
FROM c_instrumentitemelement
WHERE ParentId = :instrument-id