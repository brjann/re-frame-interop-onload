# Version 4.4
This release fixes some minor security issues in BASS. None of the fixes should have any practical impact on the use of BASS. 

## Security fixes
 - All administrative logins (including the special "admin" account) now require two-factor authentication.
 - File uploader now disallows .html, .htm, .js, .php, .svg files
 - Error messages do not leak information
 - Upgraded javascript libraries to latest version
 - Secure session cookie settings
 - Increased File uploader security 
 - Implemented Content-Security-Policy header
 - Error emails on failed sms/email only when max tries has been reached
 - Proper content type on some 404 pages
 - File name length guard added to File.php