$(document).ajaxSend(function(event, jqxhr, settings) {
	jqxhr.setRequestHeader("x-csrf-token", csrfToken);
});

/*
	Redirects are followed by the ajax request. So POST route should not answer with
	response/found (302). But rather with response/ok (200) and then a string with further
	instructions.
	ok: the post was received
	found [url]: the post was received and here is your new url
	re-auth: the post has not been received. you need to re-authenticate and re-post
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
		if(jqXHR.status == 200){
			console.log("FAKE ERROR");
			return false;
		}
		if (jqXHR.status == 440) {
			//$("#re-auth-modal").modal();
			$("#main-body").hide();
			$("#re-auth-box").show();
			re_auth_hidden_form = form;
		}
		form_events(form, jqXHR);

		if($(form).data("on-error") != undefined){
			var on_error = eval($(form).data("on-error"));
			on_error.call(form, jqXHR, textStatus, errorThrown);
		}
	}
}

function form_events(form, jqXHR){
	if (jqXHR.status == 422) {
		var event_text = jqXHR.responseText;
		if(event_text != ""){
			$(form).find("[data-show-on=" + event_text + "]").show();
			$(form).find("input[data-clear-on=" + event_text + "]").val("");
		}
	}
}

$(document).ready(function(){
	$("form").each(function(){

		var no_ajax = $(this).hasClass("no-ajax");
		var no_validate = $(this).hasClass("no-validate");
		if(!no_ajax || !no_validate) {

			// Save form's own submit function
			var formsubmit;
			if(this.onsubmit != null){
				formsubmit = this.onsubmit;
				this.onsubmit = null;
			}
			$(this).submit(function (event) {

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