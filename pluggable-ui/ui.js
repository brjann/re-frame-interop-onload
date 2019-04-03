let init_page,
   treatment_info,
   timezone,
   session_timeout = false,
   queued_ajaxes = [];

let re_auth_perform = function (tryagain) {
   let prompt_text = tryagain ? 'Wrong password. Try again.' : 'You need to re-enter your password to continue';
   let password = prompt(prompt_text);
   $.ajax('/api/re-auth',
      {
         method: 'post',
         headers: {'x-ui-init': true},
         contentType: 'application/json',
         data: JSON.stringify({password: password}),
         success: function () {
            while (queued_ajaxes.length > 0) {
               console.log('Retrying ajax.');
               let jqHXR = queued_ajaxes.pop();
               $.ajax(jqHXR);
            }
            session_timeout = false;
         },
         error: function (jqXHR) {
            if (jqXHR.status === 422) {
               re_auth_perform(true)
            } else {
               console.log('Return error');
               console.log(jqXHR);
            }
         }
      });
};

let re_auth_handler = function (jqXHR) {
   if (!session_timeout) {
      session_timeout = true;
      queued_ajaxes.push(jqXHR);
      setTimeout(re_auth_perform, 1000);
   } else {
      queued_ajaxes.push(jqXHR);
   }
};

$(document).ajaxComplete(function (_, jqXHR, options) {
   if (jqXHR.status === 440) {
      console.log('timeout!');
      re_auth_handler(options);
   }
});

// Make sure that no ajax calls are made before csrf has been retrieved
$(document).ajaxSend(function (x, y, z) {
   if (z.headers === undefined || z.headers['x-ui-init'] !== true) {
      if (init_page().state() !== 'resolved') {
         throw 'CSRF has not been retrieved';
      }
   }
});

let populate_menu = function (treatment_info) {
   let $menu_div = $('#menu');
   $menu_div.append('<a href="index.html">Dashboard</a>');
   if (treatment_info['messaging?']) {
      $menu_div.append('&nbsp;<a href="messages.html">Messages</a>');
   }
   $menu_div.append('&nbsp;<a href="modules.html">Modules</a>');
   $menu_div.append('&nbsp;<a href="privacy-notice.html">Privacy notice</a>');
   let logout = $('<a href="#">Log out</a>');
   logout.click(function () {
      $.ajax('/api/logout',
         {
            success: function () {
               window.location.href = '/';
            },
         });
   });
   $menu_div.append('&nbsp;').append(logout);
};

init_page = (function () {
   let executed = false;
   let tx_info_state = $.Deferred();
   let csrf_state = $.Deferred();
   let timezone_state = $.Deferred();
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

         console.log('Fetching treatment info');
         $.ajax('/api/user/tx/treatment-info', {
            headers: {'x-ui-init': true},
            success: function (data) {
               console.log('Treatment info fetched');
               treatment_info = data;
               populate_menu(treatment_info);
               tx_info_state.resolve();
            }
         });

         console.log('Fetching timezone');
         $.ajax('/api/user/timezone-name', {
            headers: {'x-ui-init': true},
            success: function (data) {
               console.log('Timezone fetched');
               timezone = data;
               timezone_state.resolve();
            }
         })
      }
      return $.when(csrf_state, tx_info_state, timezone_state);
   }
})();

$(document).ready(function () {
   init_page();
});

// https://jsfiddle.net/gabrieleromanato/bynaK/
(function ($) {
   $.fn.serializeFormJSON = function () {

      var o = {};
      var a = this.serializeArray();
      $.each(a, function () {
         if (o[this.name]) {
            if (!o[this.name].push) {
               o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
         } else {
            o[this.name] = this.value || '';
         }
      });
      return o;
   };
})(jQuery);

let form_json_submit = function (event) {
   let $form = $(event.target);
   let data = JSON.stringify($form.serializeFormJSON());
   $.ajax(
      '/api/user/tx/new-message',
      {
         method: 'post',
         data: data,
         contentType: 'application/json',
         success: function (x, y, z) {
            window.location.reload(true);
         },
         error: function () {

         }
      });
   event.preventDefault();
   return false;
};

// https://stackoverflow.com/questions/901115/how-can-i-get-query-string-values-in-javascript
let getParameterByName = function (name, url) {
   if (!url) url = window.location.href;
   name = name.replace(/[\[\]]/g, '\\$&');
   let regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
      results = regex.exec(url);
   if (!results) return null;
   if (!results[2]) return '';
   return decodeURIComponent(results[2].replace(/\+/g, ' '));
};

let format_date = function (date_str, format_str) {
   if (format_str === undefined) {
      format_str = 'YYYY-MM-DD';
      return moment(date_str).tz(timezone).format(format_str);
   }
};