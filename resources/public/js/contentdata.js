var content_submit;
$(document).ready(function () {

   var dataname_key_splitter = '$';
   var tab_splitter = '#';

   /* ----------------------------
       FIND CONTENT DATA ELEMENTS
      ---------------------------- */

   var find_statics = function ($container) {
      return $container.find('.contentdata').not(':input');
   };

   var find_inputs = function ($container) {
      return $container
         .find(':input')
         .not($container.find('form').children())
         .filter(function () {
            return this.name !== '' && this.name !== undefined;
         });
   };


   /* ----------------------------
             TABS CREATION
      ---------------------------- */

   var content_create_tabs = function ($content_div) {
      // TODO: This function is not optimized. Runs through all input fields multiple times
      var getMaxTabCount = function (tabbed_content) {
         var all_names = find_inputs(tabbed_content).map(function () {
            return $(this).prop('name');
         }).get();

         return _.reduce(all_names, function (memoO, input_name) {
            var data_name = input_name.split(dataname_key_splitter, 2)[0];
            var data = content_data[data_name];
            if (data === undefined) {
               return memoO;
            }

            var memoOX = _.reduce(data, function (memoI, value, input_key) {
               if (value === '') {
                  return memoI;
               }

               var a = input_key.split(tab_splitter, 2);
               if (data_name + dataname_key_splitter + a[0] !== input_name) {
                  return memoI;
               }
               var memoIX = a[1];

               if (!isInt(memoIX)) {
                  return memoI;
               }

               return Math.max(memoI, memoIX);
            }, memoO);
            return Math.max(memoO, memoOX);
         }, 1);
      };


      var addPlusTab = function (tabs_ul, tab_div, content_id, on_click) {
         var tab_count = tabs_ul.children().length;
         var tab_id = get_tab_id(content_id, (tab_count + 1));
         tabs_ul.append(ContentTabTab(tab_id, '+', on_click));
         tab_div.append(sprintf("<div class='tab-pane' id='%s'></div>", tab_id));
      };


      var get_tab_id = function (content_id, number) {
         return 'tab_' + content_id + '_' + number;
      };


      var ContentTabTab = function (tab_id, label, on_click) {
         var tab = $(sprintf("<li class='nav-item'><a class='nav-link' id ='tab_%s' data-target='#%s' data-toggle='tab'>%s</a></li>", tab_id, tab_id, label));
         tab.on('show.bs.tab', on_click);
         // https://developer.mozilla.org/en-US/docs/Web/API/Element/click_event#Safari_Mobile
         tab.find('a').click(function () {
         });
         return tab;
      };

      var setup_static_tabbed_data = function (content, index) {
         content.find('.contentdata').not('.notab').each(function () {
            var static_element = $(this);
            static_element.data('data-key', static_element.data('data-key') + tab_splitter + index);
         });
      };

      var set_tab_title_fn = function (tab_button, $tab_title_el) {
         var tab_link = tab_button.find('.nav-link');
         var empty_text = tab_link.text();
         return function () {
            if ($tab_title_el.val() === '') {
               tab_link.text(empty_text);
            } else {
               tab_link.text($tab_title_el.val());
            }
         };
      };

      var cloneContent = function (content, i, tab_button) {
         var tab_content = content.clone(true);
         // tab_content.prop('id', tab_content.prop('id' + i));
         find_inputs(tab_content).each(function () {
            $(this).prop('name', $(this).prop('name') + tab_splitter + i);
         });
         setup_static_tabbed_data(tab_content, i);

         var tab_title_el = $(tab_content).find('.tab-title').first();
         if (tab_title_el !== undefined) {
            var tab_title_fn = set_tab_title_fn(tab_button, tab_title_el);
            tab_title_el
               .on('keyup', tab_title_fn)
               .on('change', tab_title_fn)
               .data('on-data-load-value', tab_title_fn);
         }

         return tab_content;
      };

      var tab_name = text_tab;
      var content_id = $content_div.prop("id") || 'xxx';

      var tabelizer = function (container_index, container) {
         var tabbed_content_id = content_id + '_' + container_index;
         container = $(container);
         var tabbed_content = container.children().not('form').wrapAll('<div></div>').parent();
         tabbed_content.detach();
         var tab_div = $("<div class='tab-content'></div>");
         var cookie_name = 'tab-' + tabbed_content_id;

         var tabs_ul = $('<ul class="nav nav-tabs" role="tablist"></ul>');

         var on_click = function (e) {
            var tab = $(e.target);
            if (tab.text() === '+') {

               var tab_index = tabs_ul.children().length;
               tab.text(tab_name + ' ' + tab_index);
               var new_content = cloneContent(tabbed_content, tab_index, tab);
               $(tab.data('target')).append(new_content);
               add_markdown_classes();

               addPlusTab(tabs_ul, tab_div, tabbed_content_id, on_click);
            }
            Cookies.set(cookie_name, tab.data('target'));
         };

         var tab_count = getMaxTabCount(tabbed_content);
         for (var i = 1; i <= tab_count; i++) {
            var tab_id = get_tab_id(tabbed_content_id, i);
            var label = tab_name + ' ' + i;
            var tab_button = ContentTabTab(tab_id, label, on_click);
            tabs_ul.append(tab_button);
            var div = $(sprintf("<div class='tab-pane' id='%s'></div>", tab_id));
            div.append(cloneContent(tabbed_content, i, tab_button));
            tab_div.append(div);
         }

         // Set active tab based on cookie
         var active_tab_id = (Cookies.get(cookie_name) || "").substr(1);
         var active_div;
         var active_tab;
         if (active_tab_id !== '') {
            active_div = tab_div.find('#' + active_tab_id);
            active_tab = tabs_ul.find('#tab_' + active_tab_id);
         }
         if (active_div === undefined || !active_div.length) {
            active_div = tab_div.children().first();
            active_tab = tabs_ul.children().first().find('a');
         }
         active_tab.addClass('active');
         active_div.addClass('active');

         if (!$content_div.hasClass('read-only')) {
            addPlusTab(tabs_ul, tab_div, tabbed_content_id, on_click);
         }

         container.prepend(tab_div).prepend(tabs_ul);
      };

      if ($content_div.hasClass('tabbed')) {
         tabelizer(0, $content_div);
      } else {
         $('.tabbed').each(tabelizer);
      }
   };

   /* ----------------------------
         CONTENT DATA RETRIEVAL
      ---------------------------- */

   var get_content_data_post_key = function (value_name, data_name) {
      if (value_name.indexOf(dataname_key_splitter) === -1) {
         return data_name + dataname_key_splitter + value_name;
      } else {
         return value_name;
      }
   };

   var get_content_data_value = function (input_name) {
      var a = input_name.split(dataname_key_splitter, 2);
      var data_name = a[0];
      var value_name = a[1];

      if (content_data[data_name] !== undefined) {
         if (content_data[data_name][value_name] !== undefined) {
            return content_data[data_name][value_name];
         }
         /*
          * If it is not present - check if it's a tabbed and if #page = 1
          * then use non-tabbed as fallback
          */
         else if (value_name.indexOf(tab_splitter) !== -1) {
            var x = value_name.split(tab_splitter, 2);
            if (x[1] === "1") {
               return content_data[data_name][x[0]];
            }
         }
      }
      return undefined;
   };

   /* ----------------------------
         SETUP CONTENT DATA DIV
      ---------------------------- */

   var content_readonly = function ($content_div) {
      $content_div.find(':input')
         .each(function (index, input) {
            $(input).attr('disabled', 'disabled');
         })
   };

   var content_setup_statics = function ($content_div) {
      var data_name = $content_div.data('namespace');
      find_statics($content_div).each(function () {
         var element = $(this);
         var key = get_content_data_post_key(element.text(), data_name);
         element.data('data-key', key);
         element.addClass('key_' + key);
         element.text('');
      });
   };

   var content_fill_statics = function ($content_div) {
      find_statics($content_div).each(function () {
         var element = $(this);
         var key = $(this).data('data-key');
         var value = get_content_data_value(key);
         if (value === undefined) {
            value = '';
         }
         element.html(value.replace(/(?:\r\n|\r|\n)/g, '<br />'));
      });
   };

   var content_prepend_names = function ($content_div) {
      var data_name = $content_div.data('namespace');
      find_inputs($content_div)
         .each(function () {
            var input = this;
            $(input).prop('name', get_content_data_post_key(input.name, data_name));
         });
   };

   var content_fill_values = function ($content_div) {
      //TODO: Does not handle pre-checked checkboxes
      var data_name = $content_div.data('namespace');
      find_inputs($content_div)
         .each(function () {
            var input = this;
            var value = get_content_data_value(input.name);
            if (value !== undefined) {
               if (input.type === 'radio' || input.type === 'checkbox') {
                  $(input).prop('checked', value == input.value);
               } else {
                  $(input).val(value);
               }
               eval_data_property(input, 'on-data-load-value');
            }
         });
      $content_div.areYouSure();
   };

   content_submit = function (event) {
      if (event === undefined) {
         alert('Event is undefined. Please check form-ajax wrapping of onsubmit.');
         return false;
      }
      var form = event.target;
      var content_div = $(form).parent();
      var all_values = {};
      find_inputs(content_div)
         .each(function () {
            var input = this;
            if (input.type == 'radio') {
               if ($(input).prop('checked')) {
                  all_values[input.name] = $(input).val();
               }
            } else if (input.type == 'checkbox') {
               if ($(input).prop('checked')) {
                  all_values[input.name] = $(input).val();
               } else {
                  all_values[input.name] = '';
               }
            } else {
               all_values[input.name] = $(input).val();
            }
         });
      $(form).find('.content-poster').val(JSON.stringify(all_values));
      return true;
   };

   $('.treatment-content').each(function () {
      var $content_div = $(this);
      eval_data_property($content_div.children('div').first(),
         'before-data-load');
      content_prepend_names($content_div);
      content_setup_statics($content_div);
      content_create_tabs($content_div);
      content_fill_values($content_div);
      if ($(this).hasClass('read-only')) {
         content_readonly($content_div);
      }
      content_fill_statics($content_div);
   });

   main_text_ays();
   $('.readonly :input').prop('disabled', true);
   /* contentAdjustWidth();
    contentInsertPageBreaks();
    contentLayout();*/
});

function main_text_ays() {
   // TODO: Replace module-text with .main-text and use content-id as id
   $('.treatment-content.module-text').each(
      function () {
         var content_div = $(this);
         content_div.on('dirty.areYouSure', function () {
            content_div.find('.changes-saver').show();
         });
         content_div.on('clean.areYouSure', function () {
            content_div.find('.changes-saver').hide();
         });
      }
   );
}

function eval_data_property(element, data_property) {
   var val = $(element).data(data_property);
   if (val !== undefined) {
      try {
         if (typeof val === 'string') {
            val = eval(val);
         }

         if (typeof val === 'function') {
            return val.apply(element, arguments);
         } else {
            return val;
         }
      } catch (err) {
         console.log('Failed to eval', data_property, "on element", element);
         console.log(err);
      }
   }
}

function test_me() {
   alert('hej!');
}

function isInt(value) {
   return !isNaN(value) &&
      parseInt(Number(value)) == value && !isNaN(parseInt(value, 10));
}

// Used by form on success.
function main_text_save_complete() {
   $(this).find('.changes-saver').hide();
}