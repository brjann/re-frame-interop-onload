#Login

 - SMS auth
 - Timeout
 - Re-authenticate
 - Attack detector

# Ajax handling

- POST intermediate handler
- Re-post on re-authenticate
- Ajax intermediate? Perhaps wait with that?
 
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
