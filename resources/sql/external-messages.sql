
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
  `redact-text`,
  `sender-id`,
  `reply-to`)
VALUES :t*:emails;


-- :name external-message-emails-sent! :! :1
-- :doc
UPDATE external_message_email
SET `status` = "sent",
    `status-time` = :time
WHERE `id` IN(:v*:ids);


-- :name external-message-emails-redact! :! :1
-- :doc
UPDATE external_message_email
SET `message` = REPLACE(`message`, `redact-text`, '-REDACTED-'),
`redact-text` = ''
WHERE `status` = 'sent' AND `redact-text` <> '';


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
WHERE `fail-count` >= :max-fails;

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
  `message`,
  `redact-text`,
  `sender-id`)
VALUES :t*:smses;


-- :name external-message-smses-sent! :! :1
-- :doc
INSERT INTO external_message_sms
(`id`, `status`, `status-time`, `provider-id`)
VALUES :t*:sms-statuses
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`),
  `status-time` = VALUES(`status-time`),
  `provider-id` = VALUES(`provider-id`);


-- :name external-message-smses-redact! :! :1
-- :doc
UPDATE external_message_sms
SET `message` = REPLACE(`message`, `redact-text`, '-REDACTED-'),
`redact-text` = ''
WHERE `status` = 'sent' AND `redact-text` <> '';


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
WHERE `fail-count` >= :max-fails;

