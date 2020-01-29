# Version 4.5


## Assessment flagging
 - The flagging of assessments is now handled by Clojure instead of PHP. Faster & more reliable
 - Assessments that have been flagged for being late are closed immediately after the participant completes them


## Under the hood
 - The PHP app can communicate with the Clojure app through a REPL. This means that logic can be moved from 
 PHP to Clojure, which reduces duplication and risk for weird behavior.
 - Participant password hashing is done through the REPL instead of http requests = faster 