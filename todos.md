- Is last assessment updated?

# Login
- I lost my phone

# Login - Done
 - Re-authenticate
    - Save URL in GET
 - Anti-forgery token is no longer used with login.
 - Removed auto-completes in double-auth
 
 
 

# Instruments
- Allow only min or only max
- Images from file.php
- Page X of Y

# Ajax handling
- Spinner
- Access forbidden 304
- Server side error 500

## Done
- Create middle-ware for ajax interception
- POST intermediate handler
- No internet connection
- DISCARD Re-post on re-authenticate
 

https://clojure.github.io/clojure/clojure.test-api.html#clojure.test/*load-tests*
 
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



# TREATMENT
- Treatment active?
- Module list
- Tabs in content
- Pressing return does not submit
- New messages icon does not show up
- Custom assessment kraschar

# IMORGON
- Testa om omladdning av "vanliga" ns också kraschar tester
    - isf gå tillbaka bland commits och identifiera när det blev så
- Gör utloggningsknapp
- Fixa file.php, kolla om det finns bra skydd mot ..