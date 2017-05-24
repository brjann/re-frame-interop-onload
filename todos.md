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
 - Anti-forgery token is not used with login.
 - Removed auto-completes in double-auth

# Instruments
- Allow only min or only max

# Ajax handling
- Spinner
- No internet connection

## Wait
- Re-post on re-authenticate

## Done
- Create middle-ware for ajax interception
- POST intermediate handler
 
 
 
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
- Timezone settings

##Done 
- All HTML templates now based on base.html
- Basic validation of required form fields

#i18n

- Add translations to all templates
 
## Done

- Tags and filter plugins in Selmer that utilize Tempura
- Added handling of missing translation


#Server side GET and POST errors

## Done
- Used schema and forced validation of functions that accept GET and POST

# Other
 - Page title
 - Logging
 - Database exceptions
 - SQL injection
 - WTF? http://stackoverflow.com/questions/26515700/mysql-jdbc-driver-5-1-33-time-zone-issue