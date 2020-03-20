# Version 4.6
This version includes many improvements under the hood. Primarily, the 
move of logic from PHP to Clojure continues.

## SMS can be sent through Twilio
We have had problems with sending SMS to the United States. We have
therefore implemented an integration to the American SMS provider 
Twilio.
- Databases can now use Twilio or SMS-teknik (not both)
- It is now also possible for a database to have their own SMS-teknik 
  or Twilio account.   

## Unhandled participants tasks email moved from PHP to Clojure
- The unhandled participant tasks email reminder is now sent by Clojure
  instead of PHP. 
- It has been improved so that unhandled tasks for participants without 
  an assigned therapist are sent by email to project or database email 
  addresses.
- The emails contain a bit more information about the unhandled tasks.

## Answers flagger moved from PHP to Clojure
The answers flagger (e.g. "high depression total score") has been 
rewritten in Clojure. This has resulted in a few improvements and 
changes:
- Answers are flagged immediately instead of a few hours after the
  participant has completed an instrument.
- The syntax for flagging is the same as the scoring syntax, which means
  that flags can be triggered by multiple conditions (e.g., sum > 30 and
  item 1 > 3). This has led to some changes in syntax - the syntax of 
  your current flag settings has been automatically adjusted.
- The testing of flag conditions has changed. Instead of testing on 
  answers already in the database, the conditions are applied when 
  previewing instruments.

## Moved tasks from PHP to Clojure
- Unhandled participant tasks mailer.
- Temporary files deleter.
- Temporary tables deleter.
- Mailer queue task.

## Under the hood
- Move email and SMS logs from the ORM class hierarchy into ordinary 
  tables. Reduces number of DB requests when accessing PHP interface.
- PHP app sends emails and SMSes via the Clojure app (through REPL). 
  Reduces duplication and allows for removal of PHP Mailer queue.    
- Rewrite scheduler so that daily timezone dependent tasks work. 
- Moved client db credentials from local_x.php files to common db.
- Refactored db namespaces.
- Refactored PHP interop to use uid in atoms instead of temporary files,
  made possible by the REPL.
- Add API for embedded admin sessions in clojure which allows for 
  retrieving information about participants' treatment.
- (A temporary task was added to 4.5 and removed in 4.6. The task 
  collects all non-final statuses for SMS and emails instead of waiting
  for a therapist to view the until status collection.)
- Rewrite of tests to allow async test and reliance on objects present 
  in the test database.