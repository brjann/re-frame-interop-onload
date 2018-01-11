$(document).ajaxSend(function(event, jqxhr, settings) {
	jqxhr.setRequestHeader("x-csrf-token", csrfToken);
});

/*
 Redirects are followed by the ajax request. So POST route does not answer with
	response/found (302). But rather with response/ok (200) and then a string with further
 instructions. found [url]: the post was received and here is your new url.
 This is achieved by middleware in the app that changes "302 url" responses to
 "200 found url" responses - for ajax posts.
 If page should just be reloaded, then the url returned should simply be "reload".
 */

function post_success(form){
	return function(data, textStatus, jqXHR) {
		if($(form).data("on-success") != undefined){
			var on_success = eval($(form).data("on-success"));
			on_success.call(form, data, textStatus, jqXHR);
		}
		if (typeof data === 'string') {
			var response = data.split(" ");
			if (response[0] == "found") {
				if (response[1] == "reload") {
					window.location.reload(true);
				}
				else {
					window.location.href = response[1];
				}
			}
		}
	}
}

var re_auth_hidden_form;

function post_error(form){
	return function(jqXHR, textStatus, errorThrown) {
		var response = jqXHR.responseText;
		if(jqXHR.status == 0){
			alert(text_offline_warning);
			return false;
		}

		// http://stackoverflow.com/questions/6186770/ajax-request-returns-200-ok-but-an-error-event-is-fired-instead-of-success
		if(jqXHR.status == 200){
			// TODO: This can't remain in production
			console.log("FAKE ERROR");
			return false;
		}

		if(jqXHR.status == 403){
			if(response == "login"){
				alert(text_timeout_hard);
				window.location.href = "/login"
			}
			else{
				alert(text_try_reloading);
			}
		}

		if(jqXHR.status == 500 || jqXHR.status == 404 || jqXHR.status == 400){
			alert(text_try_reloading + " Error " + jqXHR.status);
		}

		if (jqXHR.status == 440) {
			$("#main-body").hide();
			$("#re-auth-box").show();
			re_auth_hidden_form = form;
		}
		form_ajax_response(form, jqXHR);

		if($(form).data("on-error") != undefined){
			var on_error = eval($(form).data("on-error"));
			on_error.call(form, jqXHR, textStatus, errorThrown);
		}
	}
}

function form_ajax_response(form, jqXHR) {
	// TODO: Clear previous show()??
	if (jqXHR.status == 422) {
		var event_text = jqXHR.responseText;
		if (event_text.substring(0, 7).toLowerCase() == "message") {
			var message = event_text.substr(8);
			var message_div = $(".ajax-error-message").first();
			if (message_div) {
				message_div.text(message).show();
				$(form).data('on_ajax_fns').push(function () {
					message_div.text('').hide();
				})
			}
			else {
				alert(message);
			}
		}
		else if (event_text != "") {
			//$(form).find("[data-show-on=" + event_text + "]").show();
			$(form)
				.find("[data-show-on=" + event_text + "]")
				.each(function () {
					var show_element = $(this);
					show_element.show();
					$(form).data('on_ajax_fns').push(function () {
						show_element.hide();
					})
				});
			$(form).find("input[data-clear-on=" + event_text + "]").val("");
		}
	}
}

$(document).ready(function(){
	$("form").each(function(){

		var $form = $(this);

		// Don't tamper with get forms
		if ($form.prop('method') == 'get') {
			return;
		}

		var no_ajax = $form.hasClass("no-ajax");
		var no_validate = $form.hasClass("no-validate");
		if(!no_ajax || !no_validate) {

			$form.data('on_ajax_fns', []);


			// Save form's own submit function
			var formsubmit;
			if(this.onsubmit != null){
				formsubmit = this.onsubmit;
				this.onsubmit = null;
			}

			$form.submit(function (event) {

				// TODO: Moved event.preventDefault without really knowing the effects.

				if(!no_validate) {
					var validation_failed = false;
					$form.find(".required").each(function () {
						var $input = $(this);
						if ($input.val() == "") {
							$input.parent().addClass('has-danger');
							$input.change(function () {
								$input.parent().removeClass("has-danger");
							});
							validation_failed = true;
						}
						else {
							$input.parent().removeClass('has-danger');
						}
					});
					if (validation_failed) {
						event.preventDefault();
						return false;
					}
				}

				// If form has own submit function, call it
				// if it returns false, then abort.
				if(formsubmit !== undefined){
					if(!formsubmit()){
						event.preventDefault();
						return false;
					}
				}

				if(!no_ajax){
					event.preventDefault();
					var ajax_fns = $form.data('on_ajax_fns');

					while (ajax_fns.length) {
						ajax_fns.pop()();
					}

					var post = $form.serializeArray();
					var url;
					if (this.action != "") {
						url = this.action;
					}
					else {
						url = document.URL;
					}
					$.ajax(
						url,
						{
							method: "post",
							data: post,
							success: post_success(this),
							error: post_error(this)
						}
					);
				}
			});
		}
	})
});

/*
--------------------
   RE-AUTH MODAL
--------------------
 */

function re_auth_modal_success(){
	$(this).find(".alert").hide();
	$(this).find("input").val("");
}

function re_auth_modal_error(jqXHR){
	if(jqXHR.status == 422){
		$("#main-body").hide();
		$("#re-auth-box").show();
	}
}

function re_auth_modal_submit(){
	$("#re-auth-box").hide();
	$("#main-body").show();
	$(re_auth_hidden_form).find("button[type=submit]").first().focus();

	return true;
}


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
		window.location.href = "/confirm_logout";
	}
}