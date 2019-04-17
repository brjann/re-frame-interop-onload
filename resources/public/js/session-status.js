var renew_session_password_success, renew_session_click_success;

$(document).ready(function () {
   var interval_handle,
      first_run = true,
      timeout_soon = false;

   renew_session_password_success = function () {
      timeout_soon = false;
      $(this).find('.alert').hide();
      $(this).find('input').val('');
      console.log('Password success!');
      $('#renew-session-password-modal').modal('hide');
   };

   renew_session_click_success = function () {
      timeout_soon = false;
      console.log('Click success!');
      $('#renew-session-click-modal').modal('hide');
   };

   var session_checker_success = function (data) {
      console.log(data);

      if (first_run && data === null) {
         console.log('First run and no session info, clearing interval');
         clearInterval(interval_handle);
         return;
      }
      var hard = data.hard,
         re_auth = data['re-auth'];
      if (hard <= session_timeout_hard_soon && !timeout_soon) {
         timeout_soon = true;
         console.log('Timeout soon!');
         if (re_auth === null) {
            $('#renew-session-click-modal').modal();
         } else {
            $('#renew-session-password-modal').modal();
         }
      }
   };

   var session_checker = function () {
      $.ajax('/api/session/status',
         {
            success: session_checker_success
         })
   };

   if (in_session) {
      interval_handle = setInterval(session_checker, 1000 * 5);
   } else {
      console.log('Not in session - no session checker');
   }
});
