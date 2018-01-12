var message_saving = false;
$(document).ready(function(){
	$("#new-message-form").each(
		function(){
			var text_input = $(this).find("[name='text']");
			var subject_input = $(this).find("[name='subject']");
			var last_text = text_input.val();
			var last_subject = subject_input.val();
			var spinner = $("#new-message-spinner");
			var button = spinner.parent();

			var save_draft = function(){
				message_saving = true;
				spinner.show();
				button.prop("disabled", true);
				var save_text = text_input.val();
				var save_subject = subject_input.val();
				$.ajax(
					"/user/message-save-draft",
					{
						method: "post",
						data: {
							text: save_text,
							subject: save_subject
						},
						success: function(data){
							last_text = save_text;
							last_subject = save_subject;
							spinner.hide();
							message_saving = false;
							button.prop("disabled", false);
						},
						// TODO: Determine what to do in case of timeout or offline mode
						error: function(jqXHR){
							spinner.hide();
							message_saving = false;
							button.prop("disabled", false);
						}
					}
				);
			};

			setInterval(function(){
				if((text_input.val() != last_text  || subject_input.val() != last_subject) && !message_saving){
					save_draft();
				}
			}, 5000);

			$("#draft-button").click(function(){
				save_draft();
			})
		});
	$("html, body").animate({scrollTop: $(document).height()}, 500);
});
