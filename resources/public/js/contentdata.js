$(document).ready(function () {
	//contentAutoGenerateForm();
	// must be called before form is initialised
	//contentTabbed();
	contentForm();
	/*$('.readonly :input').prop('disabled', true);
	 contentAdjustWidth();
	 contentInsertPageBreaks();
	 contentLayout();*/
});

function contentTabbed() {
	var tab_name = bass_data['tab_name'];
	var container_id = 0;
	$('.tabbed')
		.each(function () {
				container_id++;
				var container = $(this).wrap("<div></div>").parent();
				var content = $(this);
				content.removeClass('tabbed');
				content.detach();

				var tabs_ul = $("<ul></ul>");
				container.append(tabs_ul);

				var tab_count = getMaxTabCount(content);

				for (var i = 1; i <= tab_count; i++) {
					addContentTabTab(tabs_ul, i, tab_name + ' ' + i);
					addContentTabContent(container, cloneContent(content, i), i);
				}

				if (!(container.hasClass('readonly') || container.parents('.readonly').length)) {
					addContentTabPlus(container, tabs_ul, tab_count + 1);
				}

			// TODO: content.data.default here
				var cookie_name = 'tab.' + bass_data['content.data.default'] + '.' + container_id;

				container.tabs({
					activate: function (event, ui) {
						var index = ui.newTab.index() + 1;
						$.cookie(cookie_name, index - 1);
						if (ui.newTab.text() == '+') {
							var tab_content = cloneContent(content, index);
							fillStaticData(tab_content);
							ui.newPanel.children().first().replaceWith(tab_content);
							ui.newTab.children().first().text(tab_name + ' ' + index);
							addContentTabPlus(container, tabs_ul, index + 1);
							container.tabs("refresh");
						}
					},
					// Don't activate the plus tab even if it was the last one selected
					active: ($.cookie(cookie_name) >= tab_count ? 0 : $.cookie(cookie_name))
				});
			}
		);
}

// TODO: This function is not optimized. Runs through all input fields multiple times
function getMaxTabCount(content) {
	var all_names = content.find(':input').not('[type=submit], .contentposter').map(function () {
		return $(this).prop('name');
	}).get();

	return _.reduce(all_names, function (memoO, input_name) {
		var data = bass_data[getContentDataBASSVar(input_name)];
		if (data !== undefined) {
			var memoOX = _.reduce(data, function (memoI, value, input_key) {
					if (value === '') {
						return memoI;
					}
					var a = input_key.split('#', 2);
					if (a[0] != input_name) {
						return memoI;
					}
					var memoIX = a[1];

					if (!isInt(memoIX)) {
						return memoI;
					}

					return Math.max(memoI, memoIX);
				}, memoO
			);
			return Math.max(memoO, memoOX);
		}
	}, 1);
}

function addContentTabPlus(container, tabs_ul, i) {
	addContentTabTab(tabs_ul, i, '+');
	addContentTabContent(container, $('<div></div>'), i);
}

function addContentTabTab(tabs_ul, i, label) {
	var tab_template = "<li><a href='#{href}'>#{label}</a></li>";
	tabs_ul.append($(tab_template.replace(/#\{href\}/g, "#" + getContentTabId(i)).replace(/#\{label\}/g, label)));
}

function addContentTabContent(container, tab_content, i) {
	tab_content.wrap("<div id='" + getContentTabId(i) + "'></div>");
	container.append(tab_content.parent());
}

function cloneContent(content, i) {
	var tab_content = content.clone(true);
	// tab_content.prop('id', tab_content.prop('id' + i));
	tab_content.find(':input').not('[type=submit], .contentposter').each(function () {
		$(this).prop('name', $(this).prop('name') + '#' + i);
	});
	setupStaticDataTabbed(tab_content, i);
	return tab_content;
}

function setupStaticDataTabbed(content, index) {
	content.find('.contentdata').not('.notab').each(function () {
		$(this).text($(this).text() + '#' + index);
		//$(this).attr("value", $(this).attr("value") + '#' + index);
	});
}

function fillStaticData(content, data_name) {
	content.find('.contentdata').not(':input').each(function () {
		var value = getContentDataValue($(this).text(), data_name);
		if (value === undefined) {
			value = '';
		}
		$(this).html(value.replace(/(?:\r\n|\r|\n)/g, '<br />'));
	});
	/*
	 content.find(':input.contentdata').each(function () {
	 var value = getContentDataValue($(this).attr('value'));
	 if(value === undefined){
	 value = '';
	 }
	 $(this).attr('value', value);
	 });
	 */
}

function getContentTabId(index) {
	return 'content.tab.' + index;
}

function content_submit() {
	var form = event.target;
	var data_name = $(form).data('data-name');
	var allvalues = {};
	$(form)
		.find(':input').not('[type=submit], [name=__anti-forgery-token], .contentposter')
		.each(function () {
			var input = this;
			if (input.type == 'radio') {
				if ($(input).prop('checked')) {
					allvalues[getContentDataPostKey(input.name, data_name)] = $(input).val();
				}
			}
			else if (input.type == 'checkbox') {
				if ($(input).prop('checked')) {
					allvalues[getContentDataPostKey(input.name, data_name)] = $(input).val();
				}
				else {
					allvalues[getContentDataPostKey(input.name, data_name)] = '';
				}
			}
			else {
				allvalues[getContentDataPostKey(input.name, data_name)] = $(input).val();
			}
		});
	$(form).find('.contentposter').val(JSON.stringify(allvalues));
	return true;
}

function contentForm() {
	//TODO: Does not handle pre-checked checkboxes
	var contentform = $(".contentform").first();
	var data_name = contentform.data('data-name');
	contentform
		.append($('<input type="hidden">')
			.prop('name', bass_data['content.postname'])
			.prop('class', 'contentposter')
		)
		.find(':input').not('[type=submit]')
		.each(function () {
			var value = getContentDataValue(this.name, data_name);
			if (value !== undefined) {
				if (this.type == 'radio' || this.type == 'checkbox') {
					$(this).prop('checked', value == this.value);
				}
				else {
					$(this).val(value);
				}
			}
		});
	fillStaticData(contentform, data_name);
}

function getContentDataBASSVar(value_name) {
	var key = "content.data.";
	if (value_name.indexOf('.') == -1) {
		return key + bass_data['content.data.default'];
	}
	else {
		var a = value_name.split('.', 2);
		return key + a[0];
	}
}

function getContentDataValue(value_name, data_name) {
	var key = "content.data.";
	if (value_name.indexOf('.') == -1) {
		key = key + data_name;
	}
	else {
		var a = value_name.split('.', 2);
		key = key + a[0];
		value_name = a[1];
	}

	if (bass_data[key] !== undefined) {
		if (bass_data[key][value_name] !== undefined) {
			return bass_data[key][value_name];
		}
		/*
		 * If it is not present - check if it's a tabbed and if #page = 1
		 * then use non-tabbed as fallback
		 */
		else if (value_name.indexOf('#') != -1) {
			var x = value_name.split('#', 2);
			if (x[1] == 1) {
				return bass_data[key][x[0]];
			}
		}
	}
	return undefined;
}

function getContentDataPostKey(value_name, data_name) {
	if (value_name.indexOf('.') == -1) {
		return data_name + '.' + value_name;
	}
	else {
		return value_name;
	}
}

function contentAutoGenerateForm() {
	$('.autogenerateform').each(function () {

		// Remove all forms in content
		$(this).find('form').each(function () {
			var children = $(this).children();
			children.detach();
			$(this).wrap('<div></div>');
			var div = $(this).parent();
			$(this).remove();
			div.append(children);
		});

		// Remove all submit buttons
		$(this).find(':submit').remove();

		// Check if there are any inputs
		if ($(this).find(':input').length) {

			// If content is readonly, then just add contentform to this div
			if ($(this).hasClass('readonly')) {
				$(this).wrap('<div class="contentform"></div>');
			}
			else {
				// Else wrap in form and add submit button
				$(this).wrap($('<form class="contentform" method="POST" target=""></form>'));
				var homework = $(this).find("[name='$$$homework']");
				if (homework.length) {
					$(this).parent().append('<input type = "submit" value = "Skicka in hemuppgift" title = "Skickar in din hemuppgiftsrapport så att din behandlare kan läsa den">');
					var savebtn = $('<input type = "submit" value = "Spara utan att skicka in" title= "Sparar dina svar så att du kan skicka in dem senare">')
						.click(function () {
							homework.val(0);
						});
					$(this).parent().append(savebtn);
				}
				else {
					$(this).parent().append(sprintf('<input type = "submit" value = "%s">', bass_data['save_name']));
				}
			}
		}
	});
}

function contentAdjustWidth() {
	$(".content.width").each(function () {
		var classes = $(this).attr("class").split(' ');
		var width = _.find(classes, isInt);
		if (width !== undefined) {
			$(this).css('maxWidth', width + 'px');
		}
	});
}

function contentInsertPageBreaks() {
	$('.content div[style*="page-break-after"]').each(function () {
		if ($(this).css('page-break-after') == 'always') {
			var div = $('<div class = "pagebreak"></div>');
			$(this).replaceWith(div);

			// Move up in DOM until parent is content div
			while (!div.parent().hasClass('content')) {
				div.insertAfter(div.parent());
			}
		}
	});

	$('.content').prepend($('<div class = "pagebreak"></div>'));

	var page_count = 1;

	$('.content div.pagebreak').each(function () {
		$(this).nextUntil('div.pagebreak').wrapAll($('<div class = "contentpage"></div>'));
		var div = $(this).next();
		div.data('page', page_count++);
		$(this).remove();
	});
}

function contentLayout() {
	$('.content').each(function () {
		var page_count = $(this).find('.contentpage').length;
		if (page_count > 1) {
			var div = $('<div class="navigator"><a href="#" class = "pageprev">&lt;</a><span class = "pagenumbers"></span><a href="#" class = "pagenext">&gt;</a></div>');
			div.append($('<br>' + sprintf(bass_data['page_info'], '<span class = "currentpage"></span>', '<span class = "pagecount"></span>')));
			div.css('width', '100%');
			div.css('text-align', 'center');
			div.find('a').button();
			div.find('.pagecount').text(page_count);
			var content = this;

			cookie_name = $(this).prop('id') + '_page';

			var current_page = parseInt($.cookie(cookie_name), 10);

			div.find('.pageprev').click(function () {
				current_page = contentUpdatePages(content, current_page - 1, page_count);
				$.cookie(cookie_name, current_page);
			});

			div.find('.pagenext').click(function () {
				current_page = contentUpdatePages(content, current_page + 1, page_count);
				$.cookie(cookie_name, current_page);
			});

			$(this).prepend(div.clone(true));
			$(this).append(div.clone(true));

			//var current_page = isInt($.cookie(cookie_name)) ? $.cookie(cookie_name) : 1;
			current_page = contentUpdatePages(this, current_page, page_count);
		}
	});
}

function contentUpdatePages(content, current_page, page_count) {
	var navigator = $(content).find('.navigator');
	if (current_page <= 1 || !isInt(current_page)) {
		navigator.find('.pageprev').button("option", "disabled", true);
		current_page = 1;
	}
	else {
		navigator.find('.pageprev').button("option", "disabled", false);
		if (current_page >= page_count) {
			navigator.find('.pagenext').button("option", "disabled", true);
			current_page = page_count;
		}
		else {
			navigator.find('.pagenext').button("option", "disabled", false);
		}
	}

	navigator.find('.currentpage').text(current_page);
	$(content).find('.contentpage').each(function (index) {
		if (index == current_page - 1) {
			$(this).show();
		}
		else {
			$(this).hide();
		}
	});
	return current_page;
}