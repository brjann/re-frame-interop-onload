
# Login - Done
 - Re-authenticate
    - Save URL in GET
 - Anti-forgery token is no longer used with login.
 - Removed auto-completes in double-auth

# Ajax - Done
- Access forbidden 304 => you have been logged out
- Spinner
- Create middle-ware for ajax interception
- POST intermediate handler
- No internet connection
- DISCARD Re-post on re-authenticate
 
 
#Messages - Done
- Auto saving of message draft

# HTML - #Done 
- All HTML templates now based on base.html
- Basic validation of required form fields
- Timezone settings are in local_xxx.php


#i18n - Done
- Add translations to all templates
- Tags and filter plugins in Selmer that utilize Tempura
- Added handling of missing translation
 
#Other - Done
- Logging of pageloads
- Page title
