DROP PROCEDURE IF EXISTS create_bass_objects;
DELIMITER //
CREATE PROCEDURE create_bass_objects
(IN NewClass CHAR(45), ParentId INT, ParentProperty CHAR(100), HowMany INT)
BEGIN

	DECLARE ParentClass, ParentTableName, TableName CHAR(100);
    DECLARE x, NewObjectId, NewSortOrderMax, NewParentInterface INT;
    DECLARE class_sql, objectlist_sql TEXT;
    START TRANSACTION;
    
    SET TableName = concat("c_", lower(substr(NewClass, 2)));
    
    SELECT `ClassName` FROM objectlist WHERE `ObjectId` = ParentId INTO ParentClass;
    
	# Break with error if above query returns no row
    IF ParentClass IS NULL THEN
		SIGNAL SQLSTATE '45000'
			SET MESSAGE_TEXT = 'Parent does not exist';
		SELECT NULL AS ObjectId;
    ELSE
    
		SET ParentTableName = concat("c_", lower(substr(ParentClass, 2)));
		
		SET @parent_sql = concat("SELECT ParentInterface FROM ", ParentTableName, " WHERE ObjectId = ", ParentId, " INTO @ParentInterface");
		PREPARE stmt FROM @parent_sql;
		EXECUTE stmt;
		DEALLOCATE PREPARE stmt;
        
        SET NewParentInterface = @ParentInterface;
				
		# ALSO: Add links reverse to link creation
		# ALSO: Check sort order of links

		SELECT `SortOrderMax` FROM c_project INTO NewSortOrderMax FOR UPDATE;
		UPDATE c_project SET SortOrderMax = NewSortOrderMax + 5 * HowMany;
		
		SELECT max(ObjectId) FROM objectlist INTO NewObjectId FOR UPDATE;

		SET x = 1;
		
		SET objectlist_sql = "INSERT INTO objectlist (objectid, classname, parentid) VALUES";
		SET class_sql = concat("INSERT INTO ", TableName, " (ObjectId, ClassName, ParentId, Changed, Created, ParentProperty, ParentInterface, SortOrder)", " VALUES");
		WHILE x <= HowMany DO
			SET NewSortOrderMax = NewSortOrderMax + 5;
			SET NewObjectId = NewObjectId + 1;

			SET objectlist_sql = concat(objectlist_sql, concat("(", NewObjectId, ",'", NewClass, "',", ParentId,")"));
			SET class_sql = concat(class_sql, concat("(", NewObjectId, ",'", NewClass, "',", ParentId, ",", unix_timestamp(now()), ",", unix_timestamp(now()), ",'", ParentProperty, "',", NewParentInterface, ",", NewSortOrderMax, ")"));
			IF x < HowMany THEN
				SET objectlist_sql = concat(objectlist_sql, ",");
				SET class_sql = concat(class_sql, ",");
			END IF;
			SET x = x + 1;
		END WHILE;

		SET @objectlist_sql = objectlist_sql;
		PREPARE stmt FROM @objectlist_sql;
		EXECUTE stmt;
		DEALLOCATE PREPARE stmt;

		SET @class_sql = class_sql;
		PREPARE stmt FROM @class_sql;
		EXECUTE stmt;
		DEALLOCATE PREPARE stmt;

		SELECT NewObjectId AS ObjectId;
	END IF;
    COMMIT;
END //
DELIMITER ;