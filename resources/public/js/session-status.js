var renew_session_password_success, renew_session_click_success;

$(document).ready(function () {
   var check_session_handle,
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

      if (first_run && status_user_id !== null) {
         user_id = status_user_id;
      } else {
         if (status_user_id !== user_id) {
            clearInterval(check_session_handle);

            if (status_user_id === null && timeouts === null) {
               alert(text_session_no_session);
               window.location.href = logout_path;
            } else {
               alert(text_session_another_session);
               $('body')
                  .empty()
                  .text(text_session_another_session);
            }
            return;
         }
      }

      if (first_run && timeouts === null) {
         clearInterval(check_session_handle);
         return;
      }
      first_run = false;

      var hard, re_auth;
      if (timeouts !== null) {
         hard = timeouts['hard'];
         re_auth = timeouts['re-auth'];
      }

      if (timeouts === null || hard === 0) {
         clearInterval(check_session_handle);
         if (timeout_soon) {
            clearInterval(time_to_logout_handle);
            $timeout_modal.find('input').remove();
            $timeout_modal.find('button').remove();
            $timeout_modal.find('.button').remove();
            $timeout_modal.find('.modal-footer')
               .append($('<a class="btn btn-primary">')
                  .prop('href', logout_path)
                  .text(logout_path_text));
            $time_to_logout.text(sprintf(text_session_time_to_logout, 0, 0));
            $time_to_logout.parent()
               .append('<p>' + text_session_no_session + '</p>')
               .css('color', 'red');
         } else {
            alert(text_session_no_session);
            window.location.href = logout_path;
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

   var init_session = function () {
      var $status = $.ajax('/api/session/status');
      var $user_id = $.ajax('/api/session/user-id');
      if (logout_path === null) {
         var $logout_path = $.ajax('/api/logout-path');
         $.when($status, $user_id, $logout_path).done(function (x, y, z) {
            logout_path = z[0].path;
            logout_path_text = z[0].text;
            session_checker_success(x[0], y[0]);
         });
      } else {
         session_checker();
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
      check_session_handle = setInterval(session_checker, session_status_poll_interval);
      init_session();
   }
});
