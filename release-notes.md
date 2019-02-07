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
    - Old passwords are automatically converted into encrypted passwords.
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