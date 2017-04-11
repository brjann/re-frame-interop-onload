
-- :name get-instrument :? :1
-- :doc get info about instrument
SELECT
  FullName AS name,
  ShowName AS `show-name`,
  Abbreviation
FROM c_instrument
WHERE ObjectId = :instrument-id;


-- :name get-instrument-statics :? :*
-- :doc
SELECT
  text,
  SortOrder AS `sort-order`
FROM c_instrumentstaticelement
WHERE ParentId = :instrument-id;

-- :name get-instrument-tables :? :*
-- :doc
SELECT
  CellWidths as `cell-widths`,
  CellAlignments as `cell-alignments`,
  PageBreak `page-break`,
  SortOrder AS `sort-order`
FROM c_instrumenttableelement
WHERE ParentId = :instrument-id;


-- :name get-instrument-items :? :*
SELECT
  ObjectId AS `item-id`,
  name,
  text,
  CASE
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN LayoutString
  ELSE NULL
  END
    AS `layout`,
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
  CASE
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN ObjectId
  ELSE LayoutElement
  END
    AS `layout-id`,
  OptionSeparator AS `option-separator`,
  SortOrder AS `sort-order`
FROM c_instrumentitemelement
WHERE ParentId = :instrument-id;