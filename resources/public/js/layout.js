$(document).ready(function () {
   /*
    ----------------------------
      COMPUTING TOP NAV HEIGHT
    ----------------------------
    */
   var computed_top_nav_height = -1;

   var recompute_top_nav_height = function () {
      // If the toggler is expanded in mobile view, then the main_nav height
      // is too large. Therefore we fall back to the toggler height plus the
      // padding and margin from the main-nav.
      var top_bar_height = function () {
         var toggler_height = $('#navbar-toggler:visible').outerHeight(true);
         var main_nav = $('#main-nav');

         if (toggler_height > 0) {
            var margins = ['padding-top', 'padding-bottom', 'margin-top', 'margin-bottom'];
            var heights = _.reduce(margins, function (height, margin) {
               return height + parseInt(main_nav.css(margin));
            }, 0);
            return toggler_height + heights;
         }
         return $('#main-nav').outerHeight(true);
      };

      // Margin-bottom cannot be added to the module navigator so this is a workaround
      var selectors = [['#context-nav'], ['#module-nav-dropdown-toggler', 15]];
      computed_top_nav_height = top_bar_height() + _.reduce(selectors, function (height, el) {
         element = $(el[0] + ':visible');
         var element_height =
            (element.length ?
               element.outerHeight(true) + (el[1] ? el[1] : 0)
               : 0);
         return height + element_height;
      }, 0);
   };

   var top_nav_height = function () {
      if (computed_top_nav_height === -1) {
         recompute_top_nav_height();
      }
      return computed_top_nav_height;
   };
   $(window).resize(recompute_top_nav_height);

   /*
    -------------------------
          TITLE RESIZING
    -------------------------
   */

   var resize_title = function () {
      if ($('#page-title').length) {
         var set_title_width = function () {
            var toggler = $("#navbar-toggler:visible");
            var page_title = $('#page-title');

            if (toggler.length) {
               // 6 from the margin-left css setting
               page_title.width($('#main-navbar').innerWidth() - toggler.outerWidth() - 10);
               page_title.css('max-width', '');
            }
            else {
               page_title.width('');
            }
         };

         $(window).resize(set_title_width);
         set_title_width();
      }
   };

   /*
    -------------------------
        MARKDOWN CLASSES
    -------------------------
   */

   var add_markdown_classes = function () {
      var markdowns = $('.markdown');
      markdowns.find('img').addClass('img-fluid');
      markdowns.find('table').addClass('table');
      markdowns.find('textarea').addClass('form-control');
   };

   /*
    -------------------------
        NAVIGATION MENU
    -------------------------
   */

   var section_tags = ['h1'];

   var create_page_top = function ($module_content) {
      // Locate the first header and check if there is text above it,
      // if so, add an invisible header to the top of the page.
      // This way, it's possible to scroll to the top of the page
      // using scrollspy and the top position is saved.
      $module_content.find(section_tags.join(',')).first().each(function () {
         var header = $(this);
         if (header.prev().length) {
            header.parent().prepend('<h1 data-label="- ' + text_page_top + ' -"></h1>');
         }
      });
   };
   var init_module_sections = function ($module_content) {
      var counter = 0;
      var drop_down = $('#module-navbar .dropdown-menu');
      var module_text_id = $module_text.prop('id');

      $module_content.find(section_tags.join(',')).each(function () {
         var header = $(this);
         var title = '';
         var tag = header.prop('tagName').toLowerCase();
         if ($.inArray(tag, section_tags) >= 0) {
            counter++;
            var section_id = module_text_id + '-s' + counter;
            header.prop('id', section_id);
            if (header.data('label') === undefined) {
               header.data('label', header.text());
            }
            title = $(sprintf('<a class="dropdown-item text-truncate" href="#%s" onclick="return scroll_to_section(\'%s\');">%s</a>', section_id, section_id, header.data('label')));
            drop_down.append(title);
         }
      });
      return counter;
   };

   var init_module_scrollspy = function ($module_text, sections_count) {
      if (sections_count > 0) {
         var set_section_label = function (label) {
            $("#module-section-label").html('<i class="fa fa-caret-down" aria-hidden="true"></i>&nbsp;' + label);
         };

         var set_module_height = function () {
            $('.module-text').each(
               function () {
                  $(this).height($(window).height() - top_nav_height());
               }
            );
            var section_label = $('#module-section-label');
            var container_width = $('#module-navbar').width();
            section_label.width(container_width - section_label.position().left);
         };

         //var module_section_cookie = $module_text.prop('id') + "-section";

         var on_scrollspy = function () {
            var section = $("#module-navbar").find(".dropdown-item.active").attr("href");
            if (section.lengt) {

            }
            set_section_label($(section).data('label'));
            //Cookies.set(module_section_cookie, section);
         };

         // TODO: No position indicator if only 100%

         $module_text.scrollspy({target: '#module-navbar'});
         $(window).resize(set_module_height);
         set_module_height();

         $(window).on('activate.bs.scrollspy', on_scrollspy);

         set_section_label($('#module-content').find(":header").first().data('label'));

         /*
         if (Cookies.get(module_section_cookie) !== undefined) {
            var section = document.getElementById(Cookies.get(module_section_cookie).substr(1));
            if (section !== null) {
               $module_text.scrollTop(section.offsetTop);
            }
         }
         */
      }
      else {
         $("#module-navbar").remove();
      }
   };

   /*
    -------------------------
      MODULE POSITION SAVER
    -------------------------
   */

   var init_module_position_saver = function ($module_text) {
      var module_text_id = $module_text.prop('id');
      var texts_in_viewport = function () {
         var isElementInViewport = function (el, module_top) {
            //special bonus for those using jQuery
            if (typeof jQuery === "function" && el instanceof jQuery) {
               el = el[0];
            }

            var rect = el.getBoundingClientRect();

            return (
               // within
               (rect.top >= module_top && rect.bottom <= $(window).height()) ||
               // bottom showing
               (rect.bottom >= module_top && rect.bottom <= $(window).height()) ||
               // top showing
               (rect.top >= module_top && rect.top <= $(window).height()) ||
               // filling all
               (rect.top <= module_top && rect.bottom >= $(window).height())
            );
         };

         var texts = $('#module-content').children().find('p, div, ol, ul, h1, h2, h3');
         texts.each(function (ix, text) {
            $text = $(this);
            if ($.inArray($text.prop('tagName').toLowerCase(), section_tags) === -1) {
               $(text).prop('id', 'text-' + module_text_id + '-' + ix);
            }
         });

         var module_text_text_cookie = $module_text.prop('id') + "-text";

         if (Cookies.get(module_text_text_cookie) !== undefined) {
            var text = document.getElementById(Cookies.get(module_text_text_cookie));
            if (text !== null) {
               $module_text.scrollTop(text.offsetTop);
            }
         }

         return function () {
            var module_top = top_nav_height();
            var first_visible = _.find(texts, (function (el) {
               return isElementInViewport(el, module_top);
            }));
            if (first_visible !== undefined) {
               Cookies.set(module_text_text_cookie, first_visible.id);
            }
         };
      };
      $module_text.on('scroll', _.debounce(texts_in_viewport(), 100));
   };

   /*
    -------------------------
       TOP MARGIN RESIZER
    -------------------------
   */

   var resize_top_margin = function () {
      if ($('#top-nav').length) {
         var set_top_margin = function () {
            // It seems that it must be padding-top because margin-top seems to be considered a scrollable area
            var height = top_nav_height();
            $('#main-body').css('padding-top', height + 'px');
         };

         $(window).resize(set_top_margin);
         set_top_margin();
      }
   };

   /*
    -------------------------
       NAVIGATION RESIZER
    -------------------------
   */

   var resize_navigation_dropdown = function () {
      var dropdown = $('#module-navbar .dropdown');
      if (dropdown.length) {
         var dropdown_resizer = function () {
            var dropdown_menu = $('#module-navbar .dropdown-menu');
            var height = $(window).height() - top_nav_height() - 20;
            dropdown_menu.width(dropdown.width() - 2);
            dropdown_menu.css('max-height', height + 'px');
         };
         dropdown.on('shown.bs.dropdown', dropdown_resizer);
         $(window).resize(dropdown_resizer);
         dropdown_resizer();
      }
   };


   /*
    -----------------------------
      MODULE POSITION INDICATOR
    –----------------------------
   */

   var init_position_indicator = function ($module_text) {
      var $pi = $('#position-indicator');

      var place_pi = function () {
         var m_width = $module_text.innerWidth();
         var m_offset = $module_text.offset().left;
         var pi_width = $pi.outerWidth();
         $pi.offset({left: m_width + m_offset - pi_width, top: top_nav_height()});
      };

      var pi_shown = false;
      var show_pi = function () {
         if (!pi_shown) {
            pi_shown = true;
            $pi.stop();
            $pi.animate({opacity: 1}, 100);
         }
      };

      var hide_pi = function () {
         if (pi_shown) {
            pi_shown = false;
            $pi.animate({opacity: 0}, 500);
         }
      };

      var flashing = false;
      var flash_pi = function () {
         if (flashing) return;
         flashing = true;
         $pi.animate({opacity: 1}, 100);
         setTimeout(function () {
            first_showing = false;
            flashing = false;
            $pi.animate({opacity: 0}, 500);
         }, 2000);
      };

      var first_showing = true;
      var update_pi = function () {
         var module_content = document.getElementById('module-content');
         var text_height = $(module_content).height();
         var viewer_height = $module_text.height();
         console.log(text_height);
         console.log(viewer_height)
         if (text_height > viewer_height) {
            var scroll_pos = $module_text[0].scrollTop;
            var perc = Math.round((scroll_pos / (text_height - viewer_height)) * 100);
            $pi.text(perc + ' %');
            if (first_showing) {
               flash_pi();
            }
            else {
               show_pi();
            }
         }
         else {
            $pi.css('opacity', 0);
         }
      };

      place_pi();
      update_pi();
      $(window).resize(place_pi);
      // Strange delay of last execution if using high throttle time (like 50)
      $module_text.on('scroll', _.throttle(update_pi, 10));
      $module_text.on('scroll', _.debounce(hide_pi, 300));
   };

   /*
    -------------------------
     INVOKE LAYOUT FUNCTIONS
    -------------------------
   */

   add_markdown_classes();
   resize_title();
   resize_top_margin();
   resize_navigation_dropdown();
   var $module_text = $('.module-text');
   if ($module_text.length) {
      var $module_content = $('#module-content');
      create_page_top($module_content);
      var sections_count = init_module_sections($module_content);
      init_module_scrollspy($module_text, sections_count);
      init_module_position_saver($module_text);
      init_position_indicator($module_text);
   }
});

function scroll_to_section(section_id) {
   var $module_text = $('.module-text');
   var section = document.getElementById(section_id);
   // Not supported by all browsers it seems
   //section.scrollIntoView();
   if (section !== null) {
      $module_text.scrollTop(section.offsetTop);
   }
   return false;
}

function confirm_logout() {
   if (confirm(text_logout_confirm)) {
      window.location.href = "/logout";
   }
}