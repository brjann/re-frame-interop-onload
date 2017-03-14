$(document).ready(function(){
	$("form.message").each(
		function(){
		   var saving = false;
			var text_input = $(this).find("[name='text']");
			var subject_input = $(this).find("[name='subject']");
			var csrf = $(this).find("#__anti-forgery-token");
			var last_text = text_input.val();
			var last_subject = subject_input.val();
			setInterval(function(){
			   console.log("checking");
				if((text_input.val() != last_text  || subject_input.val() != last_subject) && !saving){
					console.log("saving new value");
					saving = true;
					$.post("/user/message-save-draft",
                  {"__anti-forgery-token": csrf.val(),
                     text: text_input.val(),
                     subject: subject_input.val()
                  },
						function(data){
							console.log(data);
							saving = false;
						});
				}
				last_text = text_input.val();
			}, 3000)
		})
});