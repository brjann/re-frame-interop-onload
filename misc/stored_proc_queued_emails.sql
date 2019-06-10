DROP PROCEDURE IF EXISTS queued_emails;
DELIMITER //
CREATE PROCEDURE queued_emails()
BEGIN
  START TRANSACTION;
	SELECT * FROM external_message_email
		WHERE `status` = "queued" FOR UPDATE ;
    # DO SLEEP(5);
    UPDATE external_message_email
		SET `status` = "sending" WHERE id > 0 AND `status` = "queued";
  COMMIT;
END //
DELIMITER ;