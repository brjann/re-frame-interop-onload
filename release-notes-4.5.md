# Version 4.5

Version 4.5 will be released on 2020-02-10.

This BASS version has a new experimental feature. BASS consists of two 
separate applications written in different languages, PHP and Clojure. 
The PHP app is older and responsible for rendering the admin interface
and the Clojure app is newer and responsible for the participant 
interface. The goal is to completely replace PHP with Clojure and this 
is done stepwise. With the new feature, the PHP app can communicate 
directly with the Clojure app through a REPL. This means that logic can 
be moved from PHP to Clojure, which reduces duplication and risk for 
unstable behavior.

## Assessment flagging
 - The flagging of assessments is now handled by Clojure instead of PHP. 
   Faster & more reliable.
 - Assessments that have been flagged for being late are closed 
   immediately after the participant completes them (instead of hours 
   later).
 - If a late flag is closed and later reflagged (because the participant
   has still not completed the assessment), a new flag is not created
   but the original is re-opened.


## Under the hood
 - Participant password hashing is done through the REPL instead of http
   requests = faster.
 - Statuses (e.g., "ongoing", "missed", "waiting") of group and 
   participant assessments shown in the admin interface are retrieved 
   through the REPL instead of calculated by PHP. 