var renew_session_password_success, renew_session_click_success;

$(document).ready(function () {
   var interval_handle,
      first_run = true,
      timeout_soon = false,
      $time_to_logout,
      time_to_logout_handle;

   renew_session_password_success = function () {
      timeout_soon = false;
      $(this).find('.alert').hide();
      $(this).find('input').val('');
      $('#renew-session-password-modal').modal('hide');
   };

   renew_session_click_success = function () {
      timeout_soon = false;
      $('#renew-session-click-modal').modal('hide');
   };

   var set_time_to_logout_text = function ($time_to_logout, hard) {
      var min = Math.floor(hard / 60);
      var sec = hard % 60;
      $time_to_logout.text(sprintf(text_time_to_logout, min, sec));
   };

   update_time_to_logout = function ($time_to_logout, hard) {
      if (time_to_logout_handle !== undefined) {
         clearInterval(time_to_logout_handle);
      }
      set_time_to_logout_text($time_to_logout, hard);
      time_to_logout_handle = setInterval(function () {
         set_time_to_logout_text($time_to_logout, --hard);
      }, 1000)
   };

   var session_checker_success = function (data) {
      console.log(data);

      if (first_run && data === null) {
         clearInterval(interval_handle);
         return;
      }
      var hard = data.hard,
         re_auth = data['re-auth'];

      if (timeout_soon) {
         update_time_to_logout($time_to_logout, hard);
      } else if (hard <= session_timeout_hard_soon) {
         timeout_soon = true;
         console.log('Timeout soon!');
         var $modal;
         if (re_auth === null) {
            $modal = $('#renew-session-click-modal');
         } else {
            $modal = $('#renew-session-password-modal');
         }
         $modal.modal();
         $time_to_logout = $modal.find('.time-to-logout');
         update_time_to_logout($time_to_logout, hard);
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
