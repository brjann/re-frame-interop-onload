#Login

 - SMS auth
 - Session timeout
    - Configuration
 - Re-authenticate
    - Configuration
 - Attack detector
 - The draft saver keeps the timeout from happening. Is that OK?
 - Invalid anti-forgery token after login - not OK.
 - Remove auto-completes in forms

## Done
 - Re-authenticate
    - Save URL in GET


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
 
#HTTP

 - Force reload

#Basics
- Fix HTML templating
- Form validation https://webdesign.tutsplus.com/tutorials/html5-form-validation-with-the-pattern-attribute--cms-25145

###i18n

- Add translations to all templates
- Add handling of missing translation
 
## Done

- Tags and filter plugins in Selmer that utilize Tempura


#Server side GET and POST errors

## Done
Used schema and forced validation of functions that accept GET and POST

### Specs
 
 - Client side error handling in forms and ajax
 - Responses _{:result :ok}_
 - Page title
 - Logging
 - Database exceptions
 - SQL injection
