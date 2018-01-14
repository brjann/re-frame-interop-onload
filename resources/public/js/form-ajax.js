$(document).ajaxSend(function (event, jqxhr, settings) {
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

function post_success(form, complete_fn) {
	return function (data, textStatus, jqXHR) {
		if (complete_fn !== undefined) {
			complete_fn();
		}
		if ($(form).data("on-success") != undefined) {
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

function post_error(form, complete_fn) {
	return function (jqXHR, textStatus, errorThrown) {
		if (complete_fn !== undefined) {
			complete_fn();
		}

		var response = jqXHR.responseText;
		if (jqXHR.status == 0) {
			alert(text_offline_warning);
			return false;
		}

		// http://stackoverflow.com/questions/6186770/ajax-request-returns-200-ok-but-an-error-event-is-fired-instead-of-success
		if (jqXHR.status == 200) {
			return false;
		}

		if (jqXHR.status == 403) {
			if (response == "login") {
				alert(text_timeout_hard);
				window.location.href = "/login"
			}
			else {
				alert(text_try_reloading);
			}
		}

		if (jqXHR.status == 500 || jqXHR.status == 404 || jqXHR.status == 400) {
			alert(text_try_reloading + " Error " + jqXHR.status);
		}

		if (jqXHR.status == 440) {
			$("#main-body").hide();
			$("#re-auth-box").show();
			re_auth_hidden_form = form;
		}
		form_ajax_response(form, jqXHR);

		if ($(form).data("on-error") != undefined) {
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

$(document).ready(function () {
	$("form").each(function () {

		var $form = $(this);

		// Don't tamper with get forms
		if ($form.prop('method') == 'get') {
			return;
		}

		var no_validate = $form.hasClass("no-validate");
		if (!no_validate) {
			$form.attr('novalidate', true);
		}

		var no_ajax = $form.hasClass("no-ajax");

		if (!no_ajax || !no_validate) {

			$form.data('on_ajax_fns', []);


			// Save form's own submit function
			var formsubmit;
			if (this.onsubmit != null) {
				formsubmit = this.onsubmit;
				this.onsubmit = null;
			}

			$form.submit(function (event) {

				if (!no_validate) {
					if (!$form.get(0).checkValidity()) {
						$form.addClass('was-validated');
						event.preventDefault();
						event.stopPropagation();
						return false;
					}
				}

				// If form has own submit function, call it
				// if it returns false, then abort.
				if (formsubmit !== undefined) {
					if (!formsubmit()) {
						event.preventDefault();
						event.stopPropagation();
						return false;
					}
				}

				$form.removeClass('was-validated');

				if (!no_ajax) {

					/*
					 * If using fade animation (which we're not), the spinner
					 * may not have been shown when the ajax call has been completed.
					 * Then the hide event is called before the spinner is shown,
					 * and then it is never hidden. Thus, we check if the spinner
					 * has been shown using the variable "opened". If not, delay trying
					 * to close the spinner 10 ms.
					 */
					var opened = false;
					var spinner = $('#load-spinner');

					spinner.on('shown.bs.modal', function () {
						spinner.off('shown.bs.modal');
						opened = true;
					});

					spinner.modal('show');
					spinner.find('.fa-spinner').animate({color: '#FFFFFF'}, 2000);
					$('.modal-backdrop').animate({backgroundColor: '#000000'}, 2000);

					var ajax_complete_fn = function () {
						var closer = function () {
							if (opened) {
								spinner.modal('hide');
							}
							else {
								setTimeout(closer, 10);
							}
						};
						closer();
					};

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
							success: post_success(this, ajax_complete_fn),
							error: post_error(this, ajax_complete_fn)
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

function re_auth_modal_success() {
	$(this).find(".alert").hide();
	$(this).find("input").val("");
}

function re_auth_modal_error(jqXHR) {
	if (jqXHR.status == 422) {
		$("#main-body").hide();
		$("#re-auth-box").show();
	}
}

function re_auth_modal_submit() {
	$("#re-auth-box").hide();
	$("#main-body").show();
	$(re_auth_hidden_form).find("button[type=submit]").first().focus();

	return true;
}
