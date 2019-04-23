var renew_session_password_success, renew_session_click_success;

$(document).ready(function () {
   var interval_handle,
      first_run = true,
      timeout_soon = false,
      $time_to_logout,
      time_to_logout_handle,
      $timeout_modal;

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
      $time_to_logout.text(sprintf(text_session_time_to_logout, min, sec));
   };

   var update_time_to_logout = function ($time_to_logout, hard) {
      if (time_to_logout_handle !== undefined) {
         clearInterval(time_to_logout_handle);
      }
      set_time_to_logout_text($time_to_logout, hard);
      time_to_logout_handle = setInterval(function () {
         set_time_to_logout_text($time_to_logout, --hard);
      }, 1000)
   };

   var session_checker_success = function (timeouts, status_user_id) {

      if (status_user_id !== null) {
         status_user_id = status_user_id['user-id'];
      }

      if (status_user_id !== user_id) {
         clearInterval(interval_handle);

         if (status_user_id === null && timeouts === null) {
            alert(text_session_no_session);
            window.location.href = session_timeout_return_path;
         } else {
            alert(text_session_another_session);
            $('body')
               .empty()
               .text(text_session_another_session);
         }
         return;
      }

      if (first_run && timeouts === null) {
         clearInterval(interval_handle);
         return;
      }
      first_run = false;

      var hard, re_auth;
      if (timeouts !== null) {
         hard = timeouts['hard'];
         re_auth = timeouts['re-auth'];
      }

      if (timeouts === null || hard === 0) {
         if (timeout_soon) {
            clearInterval(time_to_logout_handle);
            clearInterval(interval_handle);
            $timeout_modal.find('input').remove();
            $timeout_modal.find('button').remove();
            $timeout_modal.find('.button').remove();
            $timeout_modal.find('.modal-footer')
               .append($('<a class="btn btn-primary">')
                  .prop('href', session_timeout_return_path)
                  .text(session_timeout_return_link_text));
            $time_to_logout.text(sprintf(text_session_time_to_logout, 0, 0));
            $time_to_logout.parent()
               .append('<p>' + text_session_no_session + '</p>')
               .css('color', 'red');
         } else {
            alert(text_session_no_session);
            window.location.href = session_timeout_return_path;
         }
         return;
      }

      if (timeout_soon) {
         update_time_to_logout($time_to_logout, hard);
         return;
      }

      if (hard <= session_timeout_hard_soon) {
         timeout_soon = true;
         if (re_auth === null) {
            $timeout_modal = $('#renew-session-click-modal');
         } else {
            $timeout_modal = $('#renew-session-password-modal');
         }
         $timeout_modal.modal();
         $time_to_logout = $timeout_modal.find('.time-to-logout');
         update_time_to_logout($time_to_logout, hard);
      }
   };

   var session_checker = function () {
      var $status = $.ajax('/api/session/status');
      var $user_id = $.ajax('/api/session/user-id');
      $.when($status, $user_id).done(function (x, y) {
         session_checker_success(x[0], y[0]);
      });
   };

   if (in_session) {
      interval_handle = setInterval(session_checker, session_status_poll_interval);
      session_checker();
   }
});
