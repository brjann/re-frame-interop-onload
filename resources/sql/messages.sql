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
  WHEN lcm.LinkeeClass IS NULL THEN "*unknown*" ELSE
    CASE WHEN lcm.LinkeeClass = 'cTherapist' THEN ct.`Name` ELSE CONCAT(cp.FirstName, " ", cp.LastName) END
  END AS `sender-name`
FROM c_message AS cm
  LEFT JOIN links_c_message AS lcm
    ON cm.ObjectId = lcm.LinkerId AND lcm.PropertyName = "Sender"
  LEFT JOIN c_therapist as ct ON lcm.LinkeeId = ct.ObjectId
  JOIN c_participant as cp ON cp.ObjectId = :user-id
WHERE cm.ParentId = :user-id AND cm.Draft = 0 ORDER BY cm.SendTime DESC

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
WHERE cm.ParentId = :user-id AND cm.Draft = 1 AND lcm.LinkeeId = :user-id LIMIT 1


-- :name save-message! :! :n
-- :doc creates a new message record
UPDATE c_message
SET MessageText = :text, SendTime = unix_timestamp(now()), Subject = :subject, Changed = unix_timestamp(now()), Draft = 0
WHERE ObjectId = :message-id

-- :name save-message-draft! :! :n
-- :doc creates a new message record
UPDATE c_message
SET MessageText = :text, SendTime = unix_timestamp(now()), Changed = unix_timestamp(now()), Subject = :subject, Draft = 1
WHERE ObjectId = :message-id

-- :name set-message-sender! :! :n
-- :doc set sender of message
CALL create_bass_link(:message-id, :user-id, "Sender", "cMessage", "cParticipant")
