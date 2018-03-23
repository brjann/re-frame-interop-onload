var sms_input = $("#sms-number");

if (sms_input.length) {
   sms_input.intlTelInput({
      onlyCountries: sms_countries,
      initialCountry: sms_countries[0],
      utilsScript: "/js/intl-tel-input/js/utils.js",
      hiddenInput: "sms-number"
   });
}

function sms_change() {
   var sms_number = $('#sms-number');
   sms_number.intlTelInput('setNumber', sms_number.intlTelInput('getNumber'));
   //$('#sms-number-hidden').val(sms_number.intlTelInput('getNumber'));
}

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

function confirm_registration_dialog() {
   if ($('#registration-form').get(0).checkValidity()) {
      $('#registration-div').hide();
      $('#confirm-div').show();

      var basic_fields = ['first-name', 'last-name', 'email', 'pid-number'];
      for (var i = 0; i < basic_fields.length; i++) {
         $('#' + basic_fields[i] + '-confirm').text($('#' + basic_fields[i]).val());
      }

      var sms_number = $('#sms-number');
      if (sms_number.length) {
         $('#sms-number-confirm').text(sms_number.intlTelInput('getNumber'));
         var country = sms_number.intlTelInput('getSelectedCountryData');
         $('#sms-country').text(country['name']);
      }
      return false;
   }
   else {
      // Must return true if not valid to show form errors.
      return true;
   }
}

function change_registration() {
   $('#confirm-div').hide();
   $('#registration-div').show();
}

function confirm_registration() {
   $('#confirm-div').hide();
   $('#registration-div').show();
   $('#registration-form').submit();
}