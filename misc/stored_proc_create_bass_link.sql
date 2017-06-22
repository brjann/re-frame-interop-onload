DROP PROCEDURE IF EXISTS create_bass_link;
DELIMITER //
CREATE PROCEDURE create_bass_link
(IN LinkerId_ INT, LinkeeId_ INT, PropertyName_ CHAR(100), LinkerClass_ CHAR(100), LinkeeClass_ CHAR(100))
BEGIN

    DECLARE LinkTableName CHAR(100);
    DECLARE MaxSortOrder INT;
    START TRANSACTION;
    
    SET LinkTableName = concat("links_c_", lower(substr(LinkerClass_, 2)));
    
	SET @sort_sql = concat("SELECT Max(SortOrder) FROM ", LinkTableName, " INTO @MaxSortOrder FOR UPDATE");
	PREPARE stmt FROM @sort_sql;
	EXECUTE stmt;
	DEALLOCATE PREPARE stmt;
    
    SET MaxSortOrder = @MaxSortOrder;
    IF MaxSortOrder IS NULL THEN 
		SET MaxSortOrder = 0;
	ELSE
		SET MaxSortOrder = MaxSortOrder + 5;
	END IF;
    
    SET @insert_sql = concat("REPLACE INTO ", LinkTableName, 
		" (LinkerId, LinkeeId, PropertyName, LinkerClass, LinkeeClass, SortOrder) 
		VALUES (", 
			LinkerId_, ",",
			LinkeeId_, ",'",
			PropertyName_, "','",
			LinkerClass_, "','",
			LinkeeClass_, "',",
            MaxSortOrder,
        ")");

    
	PREPARE stmt FROM @insert_sql;
	EXECUTE stmt;
	DEALLOCATE PREPARE stmt;
	
	REPLACE INTO links_reverse VALUES (LinkeeId_, LinkerClass_);
    COMMIT;
END //
DELIMITER ;