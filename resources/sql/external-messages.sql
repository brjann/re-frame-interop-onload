
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

## ---------------
##   EMAIL QUEUE
## ---------------

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


-- :name external-message-emails-sent! :! :1
-- :doc
UPDATE external_message_email
SET `status` = "sent",
    `status-time` = :time
WHERE `id` IN(:v*:ids);


-- :name external-message-emails-update-fail-count! :! :1
-- :doc
UPDATE external_message_email
SET `fail-count` = `fail-count` + 1,
    `status` = "queued",
    `status-time` = :time
WHERE `id` IN(:v*:ids);


-- :name external-message-emails-final-failed! :! :1
-- :doc
UPDATE external_message_email
SET `status` = "failed"
WHERE `fail-count` >= :max-failures;

## ---------------
##    SMS QUEUE
## ---------------

-- :name external-message-queue-smses! :! :1
-- :doc
INSERT INTO external_message_sms
  (`user-id`,
  `created-time`,
  `status`,
  `status-time`,
  `to`,
  `message`)
VALUES :t*:smses;


-- :name external-message-smses-sent! :! :1
-- :doc
UPDATE external_message_sms
SET `status` = "sent",
    `status-time` = :time
WHERE `id` IN(:v*:ids);


-- :name external-message-smses-update-fail-count! :! :1
-- :doc
UPDATE external_message_sms
SET `fail-count` = `fail-count` + 1,
    `status` = "queued",
    `status-time` = :time
WHERE `id` IN(:v*:ids);


-- :name external-message-smses-final-failed! :! :1
-- :doc
UPDATE external_message_sms
SET `status` = "failed"
WHERE `fail-count` >= :max-failures;