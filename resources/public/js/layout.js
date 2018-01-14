function set_title_width() {
	var toggler = $("#navbar-toggler:visible");
	var page_title = $('#page-title');

	if (toggler.length) {
		page_title.width(toggler.offset().left - page_title.offset().left);
	}
	else {
		page_title.width('');
	}
}

$(document).ready(function () {
	$(window).resize(set_title_width);
	set_title_width();
});


/*
 --------------
 MODULES
 --------------
 */


function set_module_height() {
	$('#module-text').each(
		function () {
			$(this).height($(window).height() - $(this).offset().top);
		}
	);
	var section_label = $('#module-section-label');
	var container_width = $('#module-navbar').width();
	section_label.width(container_width - section_label.position().left);

}

$(document).ready(function () {
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
			module_text.scrollspy({target: '#module-navbar'});

			var set_label = function (label) {
				$("#module-section-label").html('<i class="fa fa-caret-down" aria-hidden="true"></i>&nbsp;' + label);
			};
			$(window).resize(set_module_height);
			set_module_height();
			var module_section_cookie = $("#module-navbar").parents(".module").attr('id') + "-section";

			$(window).on('activate.bs.scrollspy', function () {
				var section = $("#module-navbar").find(".dropdown-item.active").attr("href");
				set_label($(section).text());
				Cookies.set(module_section_cookie, section);
			});
			set_label($("#module-text").find(":header").first().text());

			// TODO: Or https://stackoverflow.com/questions/2009029/restoring-page-scroll-position-with-jquery
			if (Cookies.get(module_section_cookie) !== undefined) {

				var section = document.getElementById(Cookies.get(module_section_cookie).substr(1));
				if (section !== null) {
					section.scrollIntoView();
				}

				//var url = location.href;               //Save down the URL without hash.
				//location.href = Cookies.get(module_section_cookie);                 //Go to the target element.
				//history.replaceState(null, "", url);

				//window.location.hash = Cookies.get(module_section_cookie);
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
	return false;
}

function confirm_logout() {
	if (confirm(text_logout_confirm)) {
		window.location.href = "/logout";
	}
}