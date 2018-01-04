$(document).ready(function () {

	$('.treatment-content').each(function () {
		content_prepend_names($(this));
		content_setup_statics($(this));
		if ($(this).hasClass('tabbed')) {
			content_create_tabs($(this));
		}
		content_fill_values($(this));
		content_fill_statics($(this));
	});

	main_text_ays();
	$('.readonly :input').prop('disabled', true);
	/* contentAdjustWidth();
	 contentInsertPageBreaks();
	 contentLayout();*/
});


function main_text_ays() {
	$('.treatment-content.main-text').each(
		function () {
			var content_div = $(this);
			content_div.on('dirty.areYouSure', function () {
				content_div.find('.changes-saver').show();
			});
			content_div.on('clean.areYouSure', function () {
				content_div.find('.changes-saver').hide();
			});
		}
	);
}


function isInt(value) {
	return !isNaN(value) &&
		parseInt(Number(value)) == value && !isNaN(parseInt(value, 10));
}

// Used by form on success.
function main_text_save_complete() {
	$(this).find('.changes-saver').hide();
}

function content_create_tabs(content) {

	// TODO: This function is not optimized. Runs through all input fields multiple times
	var getMaxTabCount = function (tabbed_content) {
		var all_names = tabbed_content.find(':input').not('[type=submit]').map(function () {
			return $(this).prop('name');
		}).get();

		return _.reduce(all_names, function (memoO, input_name) {
			var data = content_data[input_name.split('.', 2)[0]];
			if (data === undefined) {
				return memoO;
			}
			var data_name = input_name.split('.', 2)[0];
			var memoOX = _.reduce(data, function (memoI, value, input_key) {
				if (value === '') {
					return memoI;
				}

				var a = input_key.split('#', 2);
				if (data_name + '.' + a[0] != input_name) {
					return memoI;
				}
				var memoIX = a[1];

				if (!isInt(memoIX)) {
					return memoI;
				}

				return Math.max(memoI, memoIX);
			}, memoO);
			return Math.max(memoO, memoOX);
		}, 1);
	};


	var addPlusTab = function (tabs_ul, tab_div, content_id, on_click) {
		var tab_count = tabs_ul.children().length;
		var tab_id = get_tab_id(content_id, (tab_count + 1));
		tabs_ul.append(ContentTabTab(tab_id, '+', on_click));
		tab_div.append(sprintf("<div class='tab-pane' id='%s'></div>", tab_id));
	};


	var get_tab_id = function (content_id, number) {
		return 'tab_' + content_id + '_' + number;
	};


	var ContentTabTab = function (tab_id, label, on_click) {
		var tab = $(sprintf("<li class='nav-item'><a class='nav-link' id ='tab_%s' data-target='#%s' data-toggle='tab'>%s</a></li>", tab_id, tab_id, label));
		tab.on('show.bs.tab', on_click);
		return tab;
	};


	var cloneContent = function (content, i) {
		var tab_content = content.clone(true);
		// tab_content.prop('id', tab_content.prop('id' + i));
		tab_content.find(':input').not('[type=submit]').each(function () {
			$(this).prop('name', $(this).prop('name') + '#' + i);
		});
		setup_static_tabbed_data(tab_content, i);
		return tab_content;
	};


	var setup_static_tabbed_data = function (content, index) {
		content.find('.contentdata').not('.notab').each(function () {
			var static_element = $(this);
			static_element.data('data-key', static_element.data('data-key') + '#' + index);
		});
	};

	var tab_name = "FLIKK";
	var content_id = content.prop("id") || 'xxx';

	var tabelizer = function (container_index, container) {
		var tabbed_content_id = content_id + '_' + container_index;
		container = $(container);
		var tabbed_content = container.children().not('form').wrapAll('<div></div>').parent();
		tabbed_content.detach();
		var tab_div = $("<div class='tab-content'></div>");
		// TODO: Handle id of embedded tabbed forms
		var cookie_name = 'tab-' + tabbed_content_id;

		var tabs_ul = $('<ul class="nav nav-tabs" role="tablist"></ul>');

		var on_click = function (e) {
			var tab = $(e.target);
			if (tab.text() == '+') {

				// TODO: Static data
				//fillStaticData(tab_content);
				var tab_index = tabs_ul.children().length;
				tab.text(tab_name + ' ' + tab_index);
				var new_content = cloneContent(tabbed_content, tab_index)
				$(tab.data('target')).append(new_content);

				addPlusTab(tabs_ul, tab_div, tabbed_content_id, on_click);
			}
			Cookies.set(cookie_name, tab.data('target'));
		};

		var tab_count = getMaxTabCount(tabbed_content);
		//var tab_count = 4;
		for (var i = 1; i <= tab_count; i++) {
			var tab_id = get_tab_id(tabbed_content_id, i);
			var label = tab_name + ' ' + i;
			tabs_ul.append(ContentTabTab(tab_id, label, on_click));
			var div = $(sprintf("<div class='tab-pane' id='%s'></div>", tab_id));
			div.append(cloneContent(tabbed_content, i));
			tab_div.append(div);
		}

		// Set active tab based on cookie
		var active_tab_id = (Cookies.get(cookie_name) || "").substr(1);
		var active_div;
		var active_tab;
		if (active_tab_id != '') {
			active_div = tab_div.find('#' + active_tab_id);
			active_tab = tabs_ul.find('#tab_' + active_tab_id);
		}
		if (active_div === undefined || !active_div.length) {
			active_div = tab_div.children().first();
			active_tab = tabs_ul.children().first().find('a');
		}
		active_tab.addClass('active');
		active_div.addClass('active');

		// TODO: Don't show plus tab in readonly mode
		/*if (!(content.hasClass('readonly') || container.parents('.readonly').length)) {
		 addContentTabPlus(container, tabs_ul, tab_count + 1);
		 }*/

		addPlusTab(tabs_ul, tab_div, tabbed_content_id, on_click);

		container.prepend(tab_div).prepend(tabs_ul);
	};

	if (content.hasClass('tabbed')) {
		tabelizer(0, content);
	}
	else {
		$('.tabbed')
			.each(tabelizer);
	}
}

function content_fill_statics(content) {
	content.find('.contentdata').not(':input').each(function () {
		var input = $(this);
		var key = $(this).data('data-key');
		var value = getContentDataValue(key);
		if (value === undefined) {
			value = '';
		}
		input.html(value.replace(/(?:\r\n|\r|\n)/g, '<br />'));
	});
}

function content_submit() {
	var form = event.target;
	var content_div = $(form).parent();
	var all_values = {};
	$(content_div)
		.find(':input').not($(form).children())
		.each(function () {
			var input = this;
			if (input.type == 'radio') {
				if ($(input).prop('checked')) {
					all_values[input.name] = $(input).val();
				}
			}
			else if (input.type == 'checkbox') {
				if ($(input).prop('checked')) {
					all_values[input.name] = $(input).val();
				}
				else {
					all_values[input.name] = '';
				}
			}
			else {
				all_values[input.name] = $(input).val();
			}
		});
	$(form).find('.content-poster').val(JSON.stringify(all_values));
	return true;
}

function content_prepend_names(content_div) {
	var data_name = content_div.data('data-name');
	content_div
		.find(':input').not(content_div.find('form').children())//.not('[type=submit]')
		.each(function () {
			var input = this;
			$(input).prop('name', getContentDataPostKey(input.name, data_name));
		});
}


function content_setup_statics(content_div) {
	var data_name = content_div.data('data-name');
	content_div.find('.contentdata').not(':input').each(function () {
		var input = $(this);
		var key = getContentDataPostKey(input.text(), data_name);
		input.data('data-key', key);
		input.addClass('key_' + key);
		input.text('');
	});
}

function content_fill_values(content_div) {
	//TODO: Does not handle pre-checked checkboxes
	var data_name = content_div.data('data-name');
	content_div
		.find(':input').not(content_div.find('form').children())//.not('[type=submit]')
		.each(function () {
			var input = this;
			var value = getContentDataValue(input.name);
			if (value !== undefined) {
				if (input.type == 'radio' || input.type == 'checkbox') {
					$(input).prop('checked', value == input.value);
				}
				else {
					$(input).val(value);
				}
			}
		});
	content_div.areYouSure();
}

function getContentDataValue(value_name) {
	var a = value_name.split('.', 2);
	var key = a[0];
	value_name = a[1];

	if (content_data[key] != undefined) {
		if (content_data[key][value_name] != undefined) {
			return content_data[key][value_name];
		}
		/*
		 * If it is not present - check if it's a tabbed and if #page = 1
		 * then use non-tabbed as fallback
		 */
		else if (value_name.indexOf('#') != -1) {
			var x = value_name.split('#', 2);
			if (x[1] == 1) {
				return content_data[key][x[0]];
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