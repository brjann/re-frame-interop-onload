# Version 4.7
Will be released on 2020-06-02

Version 4.7 is focused on security improvements and fixes some long-
standing annoying issues with therapist editing and the file uploader.
The most pertinent changes are that therapist logins now have an
expiration date and that participants can set/change their own passwords.

# Login expiration date for therapists
There is now a date for how long a therapist's login is valid. This date
is automatically set to 6 months from now for all therapists whose
accounts have not been disabled.
- Expiration date cannot be more than 6 months in the future.
- Warning 30 days before login expires.
- Therapists who are admins or project admins can prolong their
  expiration date themselves.

# Improved therapist editing
- Show more info about therapists in therapist overview. This gives
  administrators improved overview over what privileges therapists have
  and how active they are. The following fields have been added:
  - Role: If therapist is database or project administrator.
  - Days since last login: Good for finding therapist accounts that should
    maybe be inactivated.
  - Days until expiration: So that administrators can see who needs their
    expiration date changed.
- Fix annoying bug where newly created therapist has to be clicked again
after first save.
- Fix annoying bug where setting a password would remove the "Must change
password" setting.

# Participants can now change their own password
Because participants have not been able to set/change their own passwords,
passwords have been sent through SMS or email to them. This is not good
practice.
- It's no longer possible to send passwords to participants through SMS.
- Instead, you can send a link through SMS or email that allows participants to
  change their password. These links are valid for 48 hrs.
- Participant editing screen shows when participant password was last changed.

# File uploader improvements
Failed file uploads are a constant issue. Two changes to improve the
experience:
- Max file size for uploads is given.
- Information if file extension is illegal.

# Other security fixes
- Bump JS libraries: jQuery, jQuery UI, and Bootstrap versions.
- Increase length of two-factor authentication codes to 6 characters.
- More database changes are logged.
- Double authentication screen is shown before password change screen.
- Increase complexity of QuickLogin ID.

# Under the hood
 - Registration: Include Swedish country code "se" as default allowed SMS number.
 - Add proper MIME type for .m4a files, so they can be played in th browser.
 - Increase REPL API timeout to 40 secs.