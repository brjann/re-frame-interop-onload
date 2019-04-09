-- :name get-all-messages :? :*
-- :doc retrieve all messages from a specific user
SELECT
  cm.ObjectId AS `message-id`,
  MessageText AS `text`,
  Subject,
  from_unixtime(SendTime) AS `send-datetime`,
  LinkeeId AS `sender-id`,
  LinkeeClass AS `sender-class`,
  CASE
    WHEN lcm.LinkeeClass = 'cParticipant' THEN 'participant' ELSE 'therapist'
  END AS `sender-type`,
  CASE
  WHEN lcm.LinkeeClass IS NULL THEN "*unknown*" ELSE
    CASE WHEN lcm.LinkeeClass = 'cTherapist'
      THEN ct.`Name`
      ELSE "" END
  END AS `sender-name`,
  (ReadTime = 0 OR ReadTime IS NULL) AS `unread?`
FROM c_message AS cm
  LEFT JOIN links_c_message AS lcm
    ON cm.ObjectId = lcm.LinkerId AND lcm.PropertyName = "Sender"
  LEFT JOIN c_therapist as ct ON lcm.LinkeeId = ct.ObjectId
  JOIN c_participant as cp ON cp.ObjectId = :user-id
WHERE cm.ParentId = :user-id AND cm.Draft = 0 AND cm.ParentProperty = "Messages" ORDER BY cm.SendTime ASC;

-- :name get-message-draft :? :1
-- :doc get draft message for a specific user
SELECT
  cm.ObjectId AS `message-id`,
  MessageText AS `text`,
  Subject,
  from_unixtime(CASE WHEN SendTime IS NULL THEN 0 ELSE SendTime END) AS `saved-datetime`
FROM c_message AS cm
  LEFT JOIN links_c_message AS lcm
    ON cm.ObjectId = lcm.LinkerId AND lcm.PropertyName = "Sender"
WHERE
  cm.ParentId = :user-id AND cm.Draft = 1 AND
  cm.ParentProperty = "Messages" AND
  lcm.LinkeeId = :user-id
LIMIT 1;


-- :name save-message! :! :n
-- :doc creates a new message record
UPDATE c_message
SET
  MessageText = :text,
  SendTime = unix_timestamp(now()),
  Subject = :subject,
  Changed = unix_timestamp(now()),
  Draft = 0,
  ReadTime = 0
WHERE ObjectId = :message-id;

-- :name save-message-draft! :! :n
-- :doc creates a new message record
UPDATE c_message
SET MessageText = :text, SendTime = unix_timestamp(now()), Changed = unix_timestamp(now()), Subject = :subject, Draft = 1
WHERE ObjectId = :message-id;

-- :name set-message-sender! :! :n
-- :doc set sender of message
CALL create_bass_link(:message-id, :user-id, "Sender", "cMessage", "cParticipant");

-- :name get-message-by-id :? :1
-- :doc
SELECT
  cm.ObjectId AS `message-id`,
  MessageText AS `text`,
  Subject,
  from_unixtime(CASE WHEN SendTime IS NULL THEN 0 ELSE SendTime END) AS `saved-datetime`
FROM c_message AS cm
WHERE cm.ObjectId = :message-id;

-- :name mark-message-as-read! :! :n
-- :doc
UPDATE c_message AS cm
  LEFT JOIN links_c_message AS lcm ON
    cm.ObjectId = lcm.LinkerId AND
    lcm.PropertyName = "Sender"
SET cm.ReadTime = unix_timestamp(now())
WHERE
  (cm.ReadTime = 0 OR cm.ReadTime IS NULL) AND
  cm.ParentId = :user-id AND
  cm.ObjectId = :message-id AND
  cm.ParentProperty = "Messages" AND
  (cm.ParentId != lcm.LinkeeId OR lcm.LinkeeId IS NULL);


-- :name set-message-reader! :! :n
-- :doc
CALL create_bass_link(:message-id, :user-id, "Reader", "cMessage", "cParticipant")

-- :name new-messages :? :1
-- :doc
SELECT
  count(*) AS `new-messages-count`
FROM c_message AS cm
  LEFT JOIN links_c_message AS lcm
    ON cm.ObjectId = lcm.LinkerId AND lcm.PropertyName = "Sender"
  LEFT JOIN c_therapist as ct ON lcm.LinkeeId = ct.ObjectId
  JOIN c_participant as cp ON cp.ObjectId = :user-id
WHERE
  cm.ParentId = :user-id AND
  cm.Draft = 0 AND
  cm.ParentProperty = "Messages" AND
  LinkeeClass = 'cTherapist' AND
  ReadTime = 0
ORDER BY cm.SendTime ASC;