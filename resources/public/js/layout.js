$(document).ready(function () {
   /*
    --------------
    MODULES
    --------------
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

	$('.markdown').find('img').addClass('img-fluid');
	$('.markdown').find('table').addClass('table');
	$('.markdown').find('textarea').addClass('form-control');

   var create_page_top = function (module_text) {
      // Locate the first header and check if there is text above it,
      // if so, add an invisible header to the top of the page.
      // This way, it's possible to scroll to the top of the page
      // using scrollspy and the top position is saved.
      module_text.find(':header').first().each(function () {
         var header = $(this);
         if (header.prev().length) {
            header.parent().prepend('<h3 data-label="- ' + text_page_top + ' -"></h3>');
         }
      });
   };

   var init_headers = function (module_text) {
      var counter = 0;
      var drop_down = $('#module-navbar .dropdown-menu');

      module_text.find(':header').each(function () {
         var header = $(this);
         var title = '';
         // TODO: Customize the h level
         if (header.prop('tagName').indexOf('3') >= 0) {
            counter++;
            header.attr('id', 's' + counter);
            if (header.data('label') === undefined) {
               header.data('label', header.text());
            }
            title = $(sprintf('<a class="dropdown-item" href="#s%s" onclick="return scroll_to_section(\'s%s\');">%s</a>', counter, counter, header.data('label')));
            drop_down.append(title);
         }
      });
      return counter;
   };

   var init_module_navbar = function (module_text, headers_count) {
      if (headers_count > 0) {
         var set_section_label = function (label) {
            $("#module-section-label").html('<i class="fa fa-caret-down" aria-hidden="true"></i>&nbsp;' + label);
         };

         var set_module_height = function () {
            $('#module-text').each(
               function () {
                  $(this).height($(window).height() - $(this).offset().top);
               }
            );
            var section_label = $('#module-section-label');
            var container_width = $('#module-navbar').width();
            section_label.width(container_width - section_label.position().left);
         };

         var module_section_cookie = $("#module-navbar").data('module-id') + "-section";

         var on_scrollspy = function () {
            var section = $("#module-navbar").find(".dropdown-item.active").attr("href");
            set_section_label($(section).data('label'));
            Cookies.set(module_section_cookie, section);
         };

         module_text.scrollspy({target: '#module-navbar'});

         $(window).resize(set_module_height);
         set_module_height();

         $(window).on('activate.bs.scrollspy', on_scrollspy);

         set_section_label($("#module-text").find(":header").first().data('label'));

         // TODO: Or https://stackoverflow.com/questions/2009029/restoring-page-scroll-position-with-jquery
         if (Cookies.get(module_section_cookie) !== undefined) {
            var section = document.getElementById(Cookies.get(module_section_cookie).substr(1));
            if (section !== null) {
               module_text.scrollTop(section.offsetTop);
               // Not supported by all browsers it seems
               //section.scrollIntoView();
            }
         }
      }
      else {
         $("#module-navbar").remove();
      }
   };

   var resize_top_nav = function () {
      if ($('#top-nav').length) {
         var set_top_margin = function () {
            // It seems that it must be padding-top because margin-top seems to be considered a scrollable area
            $('#main-body').css('padding-top', $('#top-nav').height() + 'px');
         };

         $(window).resize(set_top_margin);
         set_top_margin();
      }
   };

   var module_text = $('#module-text');
   if (module_text.length) {
      create_page_top(module_text);
      var headers_count = init_headers(module_text);
      init_module_navbar(module_text, headers_count);
   }
   resize_title();
   resize_top_nav();
});

function scroll_to_section(section_id) {
	var module_text = $('#module-text');
	var section = document.getElementById(section_id);
	// Not supported by all browsers it seems
	//section.scrollIntoView();
	if (section !== null) {
		module_text.scrollTop(section.offsetTop);
	}
	return false;
}

function confirm_logout() {
	if (confirm(text_logout_confirm)) {
		window.location.href = "/logout";
	}
}