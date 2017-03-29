
-- :name get-linked-treatments :? :*
-- :doc get user's active treatments (NOTE: DOES NOT CHECK IF THEY ARE ONGOING)
SELECT
  ca.ObjectId AS `treatment-access-id`,
  ct.ObjectId AS `treatment-id`,
  ct.Name AS `treatment-name`,
  ModuleAccesses AS `module-accesses`
FROM c_treatmentaccess AS ca
  JOIN links_c_treatmentaccess AS lca
    ON ca.ObjectId = lca.LinkerId AND lca.PropertyName = "Treatment"
  JOIN c_treatment AS ct
    ON lca.LinkeeId = ct.ObjectId
WHERE ca.ParentId = :user-id;

-- :name get-treatment-modules :? :*
-- :doc treatment modules of treatment
SELECT
  cm.ObjectId AS `module-id`,
  cm.Name AS `module-name`
FROM c_treatment AS ct
  JOIN links_c_treatment AS lct
    ON ct.ObjectId = lct.LinkerId AND lct.PropertyName = "Modules"
  JOIN c_module AS cm
    ON lct.LinkeeId = cm.ObjectId
WHERE ct.ObjectId = :treatment-id
ORDER BY lct.SortOrder;