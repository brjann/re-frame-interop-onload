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

function post_success(this_){
	return function(data, textStatus, jqXHR) {
		var response = data.split(" ");
		if (response[0] == "found") {
			window.location.href = response[1];
		}
	}
}

function post_error(this_){
	return function(jqXHR) {
		if (jqXHR.status == 440) {
			$("#re-auth-modal").modal();
		}

		if (jqXHR.status == 422) {
			var text = jqXHR.responseText;
			if(text != ""){
				$(this_).find("[data-show=" + text + "]").show();
				$(this_).find("input[data-clear=" + text + "]").val("");
			}
		}
	}
}

$(document).ready(function(){
	$("form").each(function(){
		if(!$(this).hasClass("no-ajax")) {

			// Save form's own submit function
			var formsubmit;
			if(this.onsubmit != null){
				formsubmit = this.onsubmit;
				this.onsubmit = null;
			}
			$(this).submit(function (event) {

				var validation_failed = false;
				$(this).find(".required").each(function(){
					if($(this).val() == ""){
						$(this).parent().addClass('has-danger');
						$(this).change(function(){
							$(this).parent().removeClass("has-danger");
						});
						validation_failed = true;
					}
					else{
						$(this).parent().removeClass('has-danger');
					}
				});
				if(validation_failed){
					return false;
				}


				event.preventDefault();

				// If form has own submit function, call it
				// if it returns false, then abort.
				if(formsubmit !== undefined){
					if(!formsubmit()){
						return false;
					}
				}
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
	// Close spinner?
}

function re_auth_modal_error(jqXHR){
	if(jqXHR.status == 440){
		$("#re-auth-modal-form").addClass("has-danger");
		$("#re-auth-modal").modal();
	}
}

function re_auth_modal_submit(){
	event.preventDefault();

	var form = $("#re-auth-modal-form");
	var password = $("#re-auth-modal-password");

	if(password.val() == ""){
		return false;
	}

	var post = form.serializeArray();
	form.removeClass("has-danger");
	$("#re-auth-modal").modal('hide');
	password.val("");

	$.ajax(
		form.attr("action"),
		{
			method: "post",
			data: post,
			success: re_auth_modal_success,
			error: re_auth_modal_error
		}
	);

	return true;
}