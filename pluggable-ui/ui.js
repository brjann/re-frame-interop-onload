var fetch_csrf;

// Make sure that no ajax calls are made before csrf has been retrieved
$(document).ajaxSend(function (x, y, z) {
   if (z.url !== '/api/user/csrf') {
      if (fetch_csrf().state() !== 'resolved') {
         throw 'CSRF has not been retrieved';
      }
   }
});

fetch_csrf = (function () {
   let csrf_state;
   return function () {
      if (!csrf_state) {
         csrf_state = $.Deferred();
         console.log('Fetching CSRF');
         $.ajax('/api/user/csrf', {
            success: function (data) {
               let csrf = data;
               console.log('CSRF: ' + csrf);
               $(document).ajaxSend(function (event, jqxhr, settings) {
                  jqxhr.setRequestHeader("x-csrf-token", csrf);
               });
               csrf_state.resolve();
            }
         })
      }
      return csrf_state;
   }
})();