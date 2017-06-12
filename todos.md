#Login

 - SMS auth
 - Session timeout
    - Configuration
 - Re-authenticate
    - Configuration
 - Attack detector
 - Remove auto-completes in login?
 - CSRF twice in layout/render?
 - Senast inloggad

## Done
 - Re-authenticate
    - Save URL in GET
 - Anti-forgery token is no longer used with login.
 - Removed auto-completes in double-auth
 
 
 

# Instruments
- Allow only min or only max
- Images from file.php

# Ajax handling
- Spinner
- Access forbidden 304

## Done
- Create middle-ware for ajax interception
- POST intermediate handler
- No internet connection
- DISCARD Re-post on re-authenticate
 
 
 
 
#Messages

- Highlight unread messages
- Response that new message has been sent
- The draft saver keeps the timeout from happening. Is that OK?

# Done
- Auto saving of message draft



#HTTP
 - Force reload



#Basics
- Form validation https://webdesign.tutsplus.com/tutorials/html5-form-validation-with-the-pattern-attribute--cms-25145

##Done 
- All HTML templates now based on base.html
- Basic validation of required form fields
- Timezone settings are in local_xxx.php


#i18n
 
## Done
- Add translations to all templates
- Tags and filter plugins in Selmer that utilize Tempura
- Added handling of missing translation


#Server side GET and POST errors

## Done
- Used schema and forced validation of functions that accept GET and POST

# Other
 - Database exceptions
 - SQL injection
 - WTF? http://stackoverflow.com/questions/26515700/mysql-jdbc-driver-5-1-33-time-zone-issue
 
## Done
- Logging of pageloads
- Page title
