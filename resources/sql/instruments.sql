
-- :name get-instrument :? :1
-- :doc get info about instrument
SELECT
  ObjectId as `instrument-id`,
  FullName AS name,
  ShowName AS `show-name`,
  Abbreviation AS `abbreviation`,

  CASE
  WHEN (ClassicMode IS NULL)
    THEN 1
  ELSE ClassicMode
  END
    AS `classic`,

  CASE
  WHEN (ResponsiveMode IS NULL)
    THEN 0
  ELSE ResponsiveMode
  END
    AS `responsive`,

  CASE
  WHEN (ResponsiveFirstColIsNumber IS NULL)
    THEN 1
  ELSE ResponsiveFirstColIsNumber
  END
    AS `first-col-is-number`


FROM c_instrument
WHERE ObjectId = :instrument-id;


-- :name get-instrument-statics :? :*
-- :doc
SELECT
  text,
  name,

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
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN BorderBottomWidth
  ELSE NULL
  END
    AS `border-bottom-width`,

  CASE
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN BorderTopWidth
  ELSE NULL
  END
    AS `border-top-width`,

  PageBreak AS `page-break`,
  SortOrder AS `sort-order`

FROM c_instrumentstaticelement
WHERE ParentId = :instrument-id;

-- :name get-instrument-tables :? :*
-- :doc
SELECT
  CellWidths as `col-widths`,
  CellAlignments as `col-alignments`,
  PageBreak `page-break`,
  SortOrder AS `sort-order`
FROM c_instrumenttableelement
WHERE ParentId = :instrument-id;


-- :name get-instrument-items :? :*
SELECT
  ObjectId AS `item-id`,
  name,
  text,
  optional,

  -- LAYOUT --
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
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN BorderBottomWidth
  ELSE NULL
  END
    AS `border-bottom-width`,

  CASE
  WHEN (LayoutElement IS NULL OR LayoutElement = 0)
    THEN BorderTopWidth
  ELSE NULL
  END
    AS `border-top-width`,

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
    AS `check-error-text`,

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

  PageBreak AS `page-break`,
  SortOrder AS `sort-order`
FROM c_instrumentitemelement
WHERE ParentId = :instrument-id;


-- :name get-instrument-test-answers :? :1
-- :doc get test answers of instrument
SELECT
  ObjectId AS `answers-id`
FROM c_instrumentanswers
WHERE ParentId = :instrument-id;


-- :name get-instrument-scoring :? :1
-- :doc get scoring formula of instrument
SELECT
  ExpressionsById AS `scoring`,
  MissingValueValue AS `default-value`
FROM c_instrumentscoring
WHERE ParentId = :instrument-id;


-- :name save-instrument-answers! :! :n
-- :doc save instrument answers
UPDATE c_instrumentanswers
  SET
    Items = :items,
    Specifications = :specifications,
    Sums = :sums,
    ItemNames = :item-names,
    DateCompleted = unix_timestamp(now()),
    Changed = unix_timestamp(now())
  WHERE ObjectId = :answers-id;