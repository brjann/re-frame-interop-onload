# Version 4.6
This version includes many improvements under the hood. Primarily, the 
move of logic from PHP to Clojure continues.

## Unhandled participants tasks email
- The unhandled participant tasks email reminder is now sent by Clojure
  instead of PHP. 
- It has been improved so that unhandled tasks for participants without 
  an assigned therapist are sent by email to project or database email 
  addresses.
- The emails contain a bit more information about the unhandled tasks. 

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