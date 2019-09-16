## Version 4.3.1
Bugfix release. Version 4.3.1 will be released on 2019-09-16.

- SMS containing "--" can now be sent
- Custom assessments now work again (stopped working in 4.3)

# Version 4.3
Version 4.3 will be released on 2019-06-23.

(Changes marked with **NOTE** may require your action after the upgrade)

This release focuses on one thing, to improve the performance and timing of the assessment reminders 
by SMS and email.

## Assessment reminders
- Before, reminders were handled by PHP and ran sequentially for all databases on the server. Reminders
were sent "sometime after 12pm", and could in reality be sent anywhere between 12 and 5 pm. The exact 
time of when reminders were sent varied from day to day and between databases. Furthermore, the reminder 
function was sometimes slow and used much server resources.
- Now, reminders are handled by Clojure and sent within minutes of 12 pm (or at another time point, 
see below) for all databases. The reminder function has been completely rewritten in Clojure to work with 
the databases in parallel and use as little server resources as possible.
- Quick login IDs are only re-generated if they will expire within 7 days, i.e., participants do not
get a new quick login ID in every new assessment reminder.
- Administrators can now customize when reminders are actually sent, for example 8 am instead of 12pm.
This is configured under "External messages".
- **NOTE** The reminder function is very complex and although it has been thoroughly tested, it is 
difficult to predict how it will behave with the large number of databases in BASS (>40). Therefore, 
please check if reminders are sent as planned for your project. This can be done when editing a 
participant, under External messages -> Sent messages.

## Registration and assessments
- Before, users were automatically redirected to the login screening after completing a registration 
or assessments. This was confusing for many who thought that they needed to login.
- Now, they are instead presented with a more helpful message that all activities are finished and 
they can close their browser window.

# Version 4.2
Version 4.2 will be released on 2019-06-03.
(Changes marked with **NOTE** may require your action after the upgrade)

## Registration
### Resume registration possible
- Before, if registration was interrupted and you tried to re-register, you would be informed that you
  already exist in the database and it was impossible to resume. 
- Now, if certain conditions are met, the user can continue their registration if they give the exact
    same contact information (SMS/email/BankID-personnummer) as before.
- When a participant registers, they are notified that they can resume registration at a later point. 
- **NOTE** If you don't want participants to be able to resume registration, you need to uncheck the 
    "Allow participants to resume registration".

## Session
### Sessions are now persistent.
- Before, whenever the app or server needed to be restarted (usually because of upgrades), all participants
    were logged out and any registration was interrupted.
- Now, participants are still logged in even if the app or server is restarted. However, the platform will 
    be unresponsive for a while during restart.
### Participants are informed if they are about to be logged out.
- Before, if a participant started to write something and left the computer for a long while 
    (2 hours) and then continued to write and tried to save, they would be logged out and what they wrote was lost.
    We received many complaints about this.
- Now, participants are warned when there is 1 hour until they are logged out. If they are away for too long, 
    they are informed that they have been logged out and that any unsaved data has been lost. Even if data is lost, 
    they are at least given an explanation.
    
## Treatment
### New user data handling functions in modules
- Data names and data importing between different treatment contents can now be configured on module-level instead 
    of content level. 
- Module-level data imports can also be aliased.
- These changes mean that one content can be reused in multiple modules without having to have multiple copies of
    it. By using aliases, content can also import data from the same content within another module.
- More information about datanames/namespaces and the relationship between modules and content is shown in the
  treatment editor overview.
    
## Logout
### On logout, participants are directed to correct location
- Before, when user completed a registration, they were directed to the login page.
- Now, they are directed back to the registration. 

## Misc
- Previewing an instrument or a user's worksheet does not lead to a participant session in the same browser
  being destroyed.
- BankID now works in Firefox ES
    
## Under the hood
- A new API has been implemented to serve future single page application functionality
- Major refactoring of namespaces for better code separation
- Each db has it's own session cookie name to eliminate the risk of cross-db session hacking
- Incorrect /embedded/create-session uid does not throw exception


# Version 4.1
Version 4.1 will be released on 2019-02-19.
(Changes marked with **NOTE** may require your action after the upgrade)

## Registration
- **NOTE** Privacy notice added.
    - Sites that store personal data are required to inform about this through a privacy notice (Swedish _sekretessmeddelande_) according to GDPR.
    - The privacy notices are written by you as project owners, not us.
    - You therefore need to add one or more privacy notices to your database under Privacy notice.
    - Databases can have a global privacy notice and also per-project privacy notices.
    - _It is not possible for participants to log on to the platform or register for a study if no privacy notice has been entered!_ 
    - However, databases that have been upgraded from 4.0 to 4.1 have their privacy notices _temporarily disabled_ so that participants are not prevented from logging in or registering after the upgrade. 
        - If you have submitted a privacy notice before the upgrade, we will enter it for you and enable the privacy notice.
        - Otherwise, enter your privacy notice as soon as possible and then enable the privacy notice.
        - After the privacy notice has been enabled, it cannot be disabled again.
        - Finished projects that do not anticipate that participants will log in again do not need to enter a privacy notice.
    - Participants must agree to the privacy notice when registering for a study.
    - By default, participants who have not registered or are already in the database (before the upgrade) also need to agree to the privacy notice at first login.
        - If you prefer not to require consent, you can uncheck the "Participants must consent" box in the privacy notice editor.
        - Please note that we strongly suggest that you require consent also from participants who are already in the database.
    - The privacy notice can be viewed by participants during assessments and in the treatment interface.
    - The participant stats screen shows when a participant consented and what privacy notice they consented to.
- Study consent added to registration.
    - Before, the study consent would often be presented after the participant had completed the registration flow. This meant that if a participant did not consent, their data would still be stored.
    - Now, you have the option to add a study consent to the registration flow. 
    - The study consent is shown after the privacy notice.
    - The study consent can include checkboxes that the participant needs to check to be able to consent. Please note that the study consent cannot collect data from participants!
    - The participant stats screen shows when a participant consented and what study consent they consented to.
- **NOTE** In registration, the finished screen is no longer shown after an assessment.
    - Before, if a registration was followed by an assessment, it was possible to show the registration finished screen after the assessment.
    - This is no longer possible. If a registration is followed by an assessment, the registration finished screen is never shown.
    - _Therefore, any text that should follow a completed registration assessment must be part of the Thank-you text of the assessment_!  

## Participant passwords and login
- Login now requires that a privacy notice exists (see above)
- All participant passwords are now encrypted and require complexity.
    - Old passwords are automatically converted into encrypted passwords. *So, no passwords are lost!*
    - Passwords are no longer shown and cannot be recovered if lost. 
    - If a password is lost, a new password needs to be created.  
    - New passwords must include at least 8 characters, upper- and lowercase and number.
    - A password can only be seen when it's created. It can then also be sent to the participant via SMS.  
- Password lost function for participants.
    - Needs to be enabled under Security settings (we recommend **not** using the "confirm email address"-option).
    - If participants have lost their password but know their login or email address, they are flagged.
    - Note: There is no way for a participant to automatically get a new password - they are flagged if they report lost password.
- Quick login codes can now be valid for more than 99 days  
- Limited access if participants log in using quick login link. 
    - BASS allows for sending quick login links to participants which gives them immediate logged in access to the platform, without providing login credentials. This is useful for collecting follow-up data but is problematic if they can access sensitive data such as conversations with therapists or homework reports. 
    - Now, if a user has logged in using a direct link , they can complete assessments. 
    - If they are also in treatment, they are now asked for their password after the assessment in order to continue to the treatment interface. Note that two-factor authentication (by SMS or email) is not used in this case since the access to the quick login link implies that the user has access to their email or mobile phone.
    - **NOTE** If you are using quick login as the only login method for participants who are in treatment, you need to assign them a password so that they can continue to their treatment.    

## Participant interface
- Error report system for participants implemented.
    - Within the treatment interface, participants can click a “Problems” link which directs them to an error report form. 
    - Participants who submit error reports are flagged in the system and their error description is attached to the flag.
    - Therapists help the participant or alert us if they’re not able to.
    - The flag is closed by the therapist or technical support after the problem has been resolved.
    - If therapists or project managers need to report a problem, they contact us by email.
    
## Assessments and instruments
- Assessments can now be sorted by date in the assessments view of a group or a participant.
- VAS scales now adapt their width in mobile view.
- Responsive instruments are now marked in the instruments overview.
- BASS4 preview of instruments now only show desktop view for non-responsive instruments 

## Treatment boxes
- **NOTE** The syntax for importing data from other worksheets has changed. Before it was "dataname.valuename" but now it's "dataname$valuename". 
- If you have an active treatment that uses the data import function, you must revise accordingly. 

## Misc
- The whole admin interface should now be in English (please report any instances of Swedish that you see).
     
## Under the hood
 - Therapist passwords are now stored with better encryption.
 - Instrument answers are now validated both client- and server-side.
 - In the participant search, search parameters are saved in session storage instead of local browser storage (i.e., they are deleted when browser is closed).
 - All data posts are validated and coerced.
 - Centralized security components.
 - All emails and SMS in Clojure app are sent asynchronously to improve user experience.
 - Improved the embedded rendering in Clojure app. 
 - Added restarting of state components, meaning that Clojure app won't have to restart on config changes add added databases.  