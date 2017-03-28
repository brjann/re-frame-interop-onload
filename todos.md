#Login

 - SMS auth
 - Session timeout
    - Configuration
 - Re-authenticate
    - Configuration
    - Save URL in GET
 - Attack detector
 - The draft saver keeps the timeout from happening. Is that OK?
 - Invalid anti-forgery token after login - not OK.
 - Remove auto-completes in forms


# Ajax handling
- Spinner

## Wait
- Re-post on re-authenticate

## Done
    - Create middle-ware for ajax interception
    - POST intermediate handler

 
#Messages

- Highlight unread messages
- Response that new message has been sent
 
#HTTP

 - Force reload

#Basics

###i18n

Tags and filter plugins in Selmer that utilize Tempura

 - Add translations to all templates
 - Add handling of missing translation

###Server side GET and POST errors

Used schema and forced validation of functions that accept GET and POST

### Specs
 
 - Client side error handling in forms and ajax
 - Responses _{:result :ok}_
 - Page title
 - Logging
 - Database exceptions
 - SQL injection
