
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
WHERE ca.ParentId = :user-id