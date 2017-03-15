$(document).ajaxSend(function(event, jqxhr, settings) {
	jqxhr.setRequestHeader("x-csrf-token", csrfToken);
});