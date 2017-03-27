$(document).ajaxSend(function(event, jqxhr, settings) {
	jqxhr.setRequestHeader("x-csrf-token", csrfToken);
});


/* function _ajax(url, settings){
	var error = settings.error;
	var success = settings.success;
	if(error !== undefined){

	}
}
	*/


/*
	Redirects are followed by the ajax request. So POST route should not answer with
	response/found (302). But rather with response/ok (200) and then a string with further
	instructions.
	ok: the post was received
	found [url]: the post was received and here is your new url
	re-auth: the post has not been received. you need to re-authenticate and re-post
 */

function post_success(data, textStatus, jqXHR){
	console.log(jqXHR);
	var response = data.split(" ");
	if(response[0] == "found"){
		window.location.href = response[1];
	}
}

function post_error(jqXHR){
	if(jqXHR.status == 440){
		alert("You have been timed-out!");
	}
}

$(document).ready(function(){
	$("form").each(function(){
		$(this).submit(function(event){
			event.preventDefault();
			var post = $(this).serializeArray();
			/*$.post(
				document.URL,
				post,
				post_receiver
			);*/
			$.ajax(
				document.URL,
				{
					method: "post",
					data: post,
					success: post_success,
					error: post_error
				}
			);

		});
	})
});
