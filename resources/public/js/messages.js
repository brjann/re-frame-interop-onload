$(document).ready(function(){
	$("form.message").each(
		function(){
			var text_input = $(this).find("[name='text']");
			var csrf = $(this).find("#__anti-forgery-token");
			var last_text = text_input.val();
			setInterval(function(){
			   console.log("checking");
				if(text_input.val() != last_text){
					console.log("saving new value");
					$.post("/user/message-save-draft", {"__anti-forgery-token": csrf.val(), text: text_input.val()}, function(data){console.log(data)});
				}
				last_text = text_input.val();
			}, 3000)
		})
});