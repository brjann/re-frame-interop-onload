let init_page,
   treatment_info;

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
   $menu_div.append('&nbsp;<a href="logout.html">Log out</a>');
};

init_page = (function () {
   let executed = false;
   let tx_info_state = $.Deferred();
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

         console.log('Fetching treatment info');
         $.ajax('/api/user/tx/treatment-info', {
            headers: {'x-ui-init': true},
            success: function (data) {
               console.log('Treatment info fetched');
               treatment_info = data;
               populate_menu(treatment_info);
               tx_info_state.resolve();
            }
         })

      }
      return $.when(csrf_state, tx_info_state);
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
         success: function () {
            window.location.reload(true);
         },
         error: function () {

         }
      });
   event.preventDefault();
   return false;
};