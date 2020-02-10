# Version 4.6

## Moved tasks from PHP to Clojure
- Unhandled participant tasks mailer
- Temporary files deleter
- Temporary tables deleter

## Under the hood
- Rewrite scheduler so that daily timezone dependent tasks work 
- Moved client db credentials from local_x.php files to common db
- Refactored db namespaces