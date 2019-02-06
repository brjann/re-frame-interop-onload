DROP PROCEDURE IF EXISTS new_assessment_round_id;
DELIMITER //
CREATE PROCEDURE new_assessment_round_id()
BEGIN
	DECLARE new_round_id INT;
  START TRANSACTION;
    SELECT `AssessmentRoundIdNext` FROM c_project INTO new_round_id FOR UPDATE;
    IF new_round_id IS NULL THEN SET new_round_id = 0; END IF;
    IF @debug_sleep > 0 THEN DO SLEEP(@debug_sleep); END IF;
    UPDATE c_project SET AssessmentRoundIdNext = new_round_id + 1;
  COMMIT;
	SELECT new_round_id  AS `round-id`;
END //
DELIMITER ;