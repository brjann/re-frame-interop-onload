
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
      THEN ObjectId
    ELSE LayoutElement
  END
    AS `layout-id`,

  CASE
    WHEN (LayoutElement IS NULL OR LayoutElement = 0)
      THEN LayoutString
    ELSE NULL
  END
    AS `layout`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0)
    THEN ObjectId
  ELSE ResponseDefElement
  END
    AS `response-id`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0)
    THEN ResponseType
  ELSE NULL
  END
    AS `response-type`,


  -- RADIO BUTTONS AND CHECKBOXES --
  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("RD", "CB")
    THEN OptionValues
  ELSE NULL
  END
    AS `option-values`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("RD", "CB")
    THEN OptionLabels
  ELSE NULL
  END
    AS `option-labels`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("RD", "CB")
    THEN OptionSeparator
  ELSE NULL
  END
    AS `option-separator`,

  CASE
    WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("RD", "CB")
      THEN OptionSpecifications
    ELSE NULL
  END
    AS `option-specifications`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("RD", "CB")
    THEN OptionBigSpecifications
  ELSE NULL
  END
    AS `option-specifications-big`,


  -- TEXT-INPUT SMALL AND BIG --
  CASE
  WHEN ResponseType IN ("RD", "CB")
    THEN OptionJumps
  ELSE NULL
  END
    AS `option-jumps`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("TX", "ST") AND RangeMin <> ""
    THEN CAST(RangeMin AS SIGNED)
  ELSE NULL
  END
    AS `range-min`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("TX", "ST") AND RangeMax <> ""
    THEN CAST(RangeMax AS SIGNED)
  ELSE NULL
  END
    AS `range-max`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("TX", "ST")
    THEN RangeErrorText
  ELSE NULL
  END
    AS `range-error-text`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND ResponseType IN ("TX", "ST")
    THEN `RegExp`
  ELSE NULL
  END
    AS `regexp`,


  -- VAS SCALE --
  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND (ResponseType = "VS")
    THEN VASMinLabel
  ELSE NULL
  END
    AS `vas-min-label`,

  CASE
  WHEN (ResponseDefElement IS NULL OR ResponseDefElement = 0) AND (ResponseType = "VS")
    THEN VASMaxLabel
  ELSE NULL
  END
    AS `vas-max-label`,

  SortOrder AS `sort-order`
FROM c_instrumentitemelement
WHERE ParentId = :instrument-id;