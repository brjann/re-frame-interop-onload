-- :name create-bass-object! :? :1
-- :doc Create a new BASS object and return it's ObjectId
CALL create_bass_objects(:class-name, :parent-id, :property-name, 1);

-- :name create-bass-objects-without-parent! :? :1
-- :doc Create many new BASS objects and return the latest's ObjectId
CALL create_bass_objects_without_parent(:class-name, :property-name, :count);

-- :name update-objectlist-parent! :! :1
-- :doc Update an object with new parent in objectlist
UPDATE objectlist SET ParentId = :parent-id WHERE ObjectId = :object-id

-- :name delete-object-from-objectlist! :! :1
-- :doc Update an object with new parent in objectlist
DELETE FROM objectlist WHERE ObjectId = :object-id;

-- :name get-db-title :? :1
SELECT title FROM c_project WHERE ObjectId = 100;

-- :name get-db-time-zone :? :1
SELECT timezone AS `time-zone` FROM c_project WHERE ObjectId = 100;

-- :name link-property-reverse! :! :1
-- :doc
INSERT IGNORE INTO links_properties_reverse
  (LinkeeId, PropertyName, LinkerClass) VALUES(:linkee-id, :property-name, :linker-class);