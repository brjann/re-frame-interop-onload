var sms_input = $("#sms-number");

if (sms_input.length) {
   sms_input.intlTelInput({
      onlyCountries: sms_countries,
      initialCountry: sms_countries[0],
      utilsScript: "/js/intl-tel-input/js/utils.js"
   });
}

function sms_change() {
   var sms_number = $('#sms-number');
   sms_number.intlTelInput('setNumber', sms_number.intlTelInput('getNumber'));
}

var text_no_match = "{%tr registration/repeat-not-match %}";
var text_password_invalid = "{%tr registration/password-req-error %}";

function password_invalid() {
   event.target.setCustomValidity(text_password_invalid);
}

function validate_password() {
   var password = $('#password');
   var password_repeat = $('#password-repeat');
   var form = $('#registration-form');
   if (password.val() !== password_repeat.val()) {
      password_repeat.get(0).setCustomValidity(text_no_match);
   }
   else {
      password_repeat.get(0).setCustomValidity('');
   }
}

function validate_pid() {
   if (typeof pid_validator !== "undefined") {
      var error = pid_validator($(event.target).val());
      if (typeof error === 'string' && error.length > 0) {
         event.target.setCustomValidity(error);
      }
      else {
         event.target.setCustomValidity('');
      }
   }
}
