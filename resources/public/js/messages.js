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

	var visibility_updater = function (data) {
		$.map(data, function (time, id) {
			var message = $('#' + id + '.unread');
			if (message.length) {
				message.find('.visibility').text(time);
				if (time >= 10) {
					$.ajax(
						'/user/message-read',
						{
							method: 'post',
							data: {'message-id': id.substr(8)},
							success: function () {
								message.find('.visibility').animate({color: 'rgba(0, 0, 0, 0)'});
								message.find('.card-body').animate({backgroundColor: 'rgba(0, 0, 0, .06)'});
								message.find('.card-footer').animate({backgroundColor: 'rgba(0, 0, 0, .03)'});
								message.find('.fa-envelope').animate({color: 'rgba(0, 0, 0, 0)'});
								message.removeClass('unread');

								if ($('.message.therapist.unread').length === 0) {
									$('#new-message-icon').animate({color: 'rgba(0, 0, 0, 0)'});
								}
							}
						}
					);
				}
			}
		})
	};

	var unread_messages = $('.message.therapist.unread');
	if (unread_messages.length) {
		var fields = unread_messages.map(
			function (index, message) {
				return {
					selector: '#' + message.id,
					name: message.id
				};
			}
		).get();
		$.screentime({
			fields: fields,
			callback: visibility_updater,
			reportInterval: 1
		});
		$('html, body').animate({
			scrollTop: $(unread_messages[0]).offset().top + 'px'
		}, 0);
	}
	else {
		$("html, body").animate({scrollTop: $(document).height()}, 0);
	}
});
