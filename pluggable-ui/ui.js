var fetch_csrf;

// Make sure that no ajax calls are made before csrf has been retrieved
$(document).ajaxSend(function (x, y, z) {
   if (z.headers === undefined || z.headers['x-ui-init'] !== true) {
      if (fetch_csrf().state() !== 'resolved') {
         throw 'CSRF has not been retrieved';
      }
   }
});

fetch_csrf = (function () {
   let executed = false;
   let timezone_state = $.Deferred();
   let csrf_state = $.Deferred();
   return function () {
      if (!executed) {
         executed = true;
         console.log('Fetching CSRF');
         $.ajax('/api/user/csrf', {
            headers: {'x-ui-init': true},
            success: function (csrf) {
               console.log('CSRF: ' + csrf);
               $(document).ajaxSend(function (event, jqxhr, settings) {
                  jqxhr.setRequestHeader("x-csrf-token", csrf);
               });
               csrf_state.resolve();
            }
         });

         console.log('Fetching timezone');
         $.ajax('/api/user/timezone-name', {
            headers: {'x-ui-init': true},
            success: function (data) {
               console.log('Timezone: ' + data);
               timezone_state.resolve();
            }
         })

      }
      return $.when(csrf_state, timezone_state);
   }
})();