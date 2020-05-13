# Version 4.7

 - Registration: Include Swedish country code "se" as default allowed SMS number
 - Bump jQuery and jQuery UI version numbers (may cause issues).
 - Add proper MIME type for .m4a files
 - CSRF-like protection for some $_POST database mutations in PHP
 - Improve therapist editing
    - Fix annoying bug where newly created therapist has to be clicked again after first save
    - Fix annoying bug where setting a password would remove the "Must change password" setting
 - Increase two-factor authentication code for admins to 5 characters
 - More help when uploading files
    - Max file size for uploads is given
    - Information if file extension is illegal
