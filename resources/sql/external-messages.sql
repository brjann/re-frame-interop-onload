
-- :name inc-external-message-count! :! :1
-- :doc
INSERT INTO external_message_count (`type`, `day`, `count`)
				VALUES (:type, :day, 1)
				ON DUPLICATE KEY UPDATE `count` = `count` + 1;


-- :name external-message-no-reply-address :? :1
-- :doc
SELECT NoreplyAddress AS `no-reply-address`
FROM c_project
WHERE ObjectId = 100;

-- :name external-message-queue-emails! :! :1
-- :doc
INSERT INTO external_message_email
  (`user-id`,
  `created-time`,
  `status`,
  `status-time`,
  `to`,
  `subject`,
  `message`,
  `reply-to`)
VALUES :t*:emails;


-- :name external-message-queued-emails :? :*
-- :doc
SELECT * FROM external_message_email
WHERE `status` = "queued" FOR UPDATE;


-- :name external-message-unqueue-emails! :! :1
-- :doc
UPDATE external_message_email
SET `status` = "sending"
WHERE `id` IN(:v*:ids);