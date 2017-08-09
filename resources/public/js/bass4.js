$(document).ajaxSend(function(event, jqxhr, settings) {
	jqxhr.setRequestHeader("x-csrf-token", csrfToken);
});

/*
	TODO: Is this still true???
	Redirects are followed by the ajax request. So POST route should not answer with
	response/found (302). But rather with response/ok (200) and then a string with further
	instructions. found [url]: the post was received and here is your new url
 */

function post_success(form){
	return function(data, textStatus, jqXHR) {
		if($(form).data("on-success") != undefined){
			var on_success = eval($(form).data("on-success"));
			on_success.call(form, data, textStatus, jqXHR);
		}
		var response = data.split(" ");
		if (response[0] == "found") {
			window.location.href = response[1];
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


		// TODO: Switch use of "this" to "form" or similar
		// Don't tamper with get forms
		if($(this).prop('method') == 'get'){
			return;
		}

		var no_ajax = $(this).hasClass("no-ajax");
		var no_validate = $(this).hasClass("no-validate");
		if(!no_ajax || !no_validate) {
			$(this).data('on_ajax_fns', []);


			// Save form's own submit function
			var formsubmit;
			if(this.onsubmit != null){
				formsubmit = this.onsubmit;
				this.onsubmit = null;
			}
			$(this).submit(function (event) {

				var ajax_fns = $(this).data('on_ajax_fns');

				while (ajax_fns.length) {
					ajax_fns.pop()();
				}

				event.preventDefault();

				if(!no_validate) {
					var validation_failed = false;
					$(this).find(".required").each(function () {
						if ($(this).val() == "") {
							$(this).parent().addClass('has-danger');
							$(this).change(function () {
								$(this).parent().removeClass("has-danger");
							});
							validation_failed = true;
						}
						else {
							$(this).parent().removeClass('has-danger');
						}
					});
					if (validation_failed) {
						return false;
					}
				}

				// If form has own submit function, call it
				// if it returns false, then abort.
				// TODO: This possibly fails if function wants normal submission. Because of event.preventDefault
				if(formsubmit !== undefined){
					if(!formsubmit()){
						return false;
					}
				}

				if(!no_ajax){
					var post = $(this).serializeArray();
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

function test(){
	console.log("the test");
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
