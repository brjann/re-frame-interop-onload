$(document).ready(function () {
   var timeout, first_run = true;

   var session_checker_success = function (data) {
      if (first_run && data === null) {
         console.log('First run and no session info, clearing interval');
         clearInterval(timeout);
         return;
      }
      console.log(data);
      var hard = data.hard,
         re_auth = data['re-auth'];
      if (hard <= session_timeout_hard_soon) {
         ;
      }
   };

   var session_checker = function () {
      $.ajax('/api/session/status',
         {
            success: session_checker_success
         })
   };

   if (in_session) {
      timeout = setInterval(session_checker, 1000 * 5);
   } else {
      console.log('Not in session - no session checker');
   }
});