$(document).ready(function () {

	if ($('#top-nav').length) {
		var set_top_margin = function () {
			// It seems that it must be padding-top because margin-top seems to be considered a scrollable area
			$('#main-body').css('padding-top', $('#top-nav').height() + 'px');
		};

		$(window).resize(set_top_margin);
		set_top_margin();
	}

	if ($('#page-title').length) {
		var set_title_width = function () {
			var toggler = $("#navbar-toggler:visible");
			var page_title = $('#page-title');

			if (toggler.length) {
				// 6 from the margin-left css setting
				page_title.width($('#main-nav').innerWidth() - toggler.outerWidth() - 6);
			}
			else {
				page_title.width('');
			}
		};

		$(window).resize(set_title_width);
		set_title_width();
	}

	/*
	 --------------
	 MODULES
	 --------------
	 */

	var module_text = $('#module-text');
	if (module_text.length) {

		var counter = 0;
		var drop_down = $('#module-navbar .dropdown-menu');
		module_text.find(':header').each(function () {
			var header = $(this);
			// TODO: Customize the h level
			if (header.prop('tagName').indexOf('3') >= 0) {
				counter++;
				header.attr('id', 's' + counter);
				drop_down.append($(sprintf('<a class="dropdown-item" href="#s%s" onclick="return goToByScroll(\'s%s\');">%s</a>', counter, counter, header.text())));
			}
		});

		if (counter > 0) {
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
				console.log('scrolled to ' + section);
				set_section_label($(section).text());
				Cookies.set(module_section_cookie, section);
			};

			module_text.scrollspy({target: '#module-navbar'});

			$(window).resize(set_module_height);
			set_module_height();

			$(window).on('activate.bs.scrollspy', on_scrollspy);

			set_section_label($("#module-text").find(":header").first().text());

			// TODO: Or https://stackoverflow.com/questions/2009029/restoring-page-scroll-position-with-jquery
			if (Cookies.get(module_section_cookie) !== undefined) {
				var section = document.getElementById(Cookies.get(module_section_cookie).substr(1));
				if (section !== null) {
					console.log('cookie found for ' + Cookies.get(module_section_cookie));
					section.scrollIntoView();
					/*
					$('html, body').animate({
						scrollTop: $(section).offset().top + 'px'
					}, 'fast');
					*/
				}
			}
		}
		else {
			$("#module-navbar").remove();
		}
	}
});

function goToByScroll(id) {
	var section = document.getElementById(id);
	if (section !== null) {
		section.scrollIntoView();
	}
}

function confirm_logout() {
	if (confirm(text_logout_confirm)) {
		window.location.href = "/logout";
	}
}