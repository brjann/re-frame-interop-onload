function password_invalid() {
   event.target.setCustomValidity(text_password_invalid);
}

function validate_password() {
   var password = $('#password');
   var password_repeat = $('#password-repeat');
   var form = $('#registration-form');
   if (password.val() !== password_repeat.val()) {
      password_repeat.get(0).setCustomValidity(text_no_match);
   } else {
      password_repeat.get(0).setCustomValidity('');
   }
}