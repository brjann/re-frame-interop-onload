/*
	FINISHED:
		- Hovering
		 - Jumps
		 - Borders
	TODO:
		- Page breaks
		- Min-max
		- Regexp
		- Optional items
		- Completed
		- When data input into specification, select corresponding radiobutton/checkbox
		- Validation/submission
		- Are you sure
		- No distance between number and question in mobile mode
		- Gray area does not cover whole cell in desktop mode
		- Does non-responsive work?
 */


/***********************
 * INSTRUMENT RENDERING
 ***********************/
$(document).ready(function(){
	$("#instrument").each(
		function(){
			var cells = [];
			var show_name = instrument["show-name"];
			$("#instrument-show-name").text(show_name);

			// TODO: Remove debug
			$("#instrument-show-name").click(function(){toggle_size($("#instrument"))});

			// TODO: There seems to be at least one too many div levels?
			var instrument_div = $("#instrument-elements");

			if(instrument.classic){
				$(this).addClass('classic');
			}

			if(instrument.responsive){
				$(this).addClass('responsive');
			}

			if(instrument['first-col-is-number']){
				$(this).addClass('first-col-is-number');
			}

			//$(this).addClass('desktop');

			var table_div = $('<div class="table"></div>').appendTo(instrument_div);
			$.each(instrument.elements, function(index, element){
				// Rename all uses of "cell" to "col"
				if(element.cells != undefined){
					cells = parse_cells(element.cells);
				}
				else{
					var item_attrs = '';
					var layout_obj;
					if(element["item-id"] != undefined){
						layout_obj = instrument.layouts[element["layout-id"]];
						item_attrs = "class = 'item-div' id = 'item-" + element['item-id'] + "'";
					}
					else{
						layout_obj = instrument["static-layouts"][element["layout-id"]];
					}
					var row_div = $(sprintf("<div %s></div>", item_attrs)).appendTo(table_div);

					// TODO: This probably allows empty items to get a div
					// var layout = (layout_obj == undefined) ? "[T]" : layout_obj.layout;
					var layout = layout_obj.layout || "[T]";

					var response = instrument.responses[element["response-id"]];
					var response_html;
					if(element["item-id"] != undefined){
						response_html = parse_response(element, response);
					}
					else response_html = "";

					var element_html = parse_element_layout(element, layout, response_html, cells, is_break_separator(response));
					if(element_html.indexOf("<!--desktop-->") >= 0){
						row_div.addClass('desktop-only');
					}
					else if(element_html.indexOf("<!--mobile-->") >= 0){
						row_div.addClass('mobile-only');
					}

					if(layout_obj['border-bottom-width'] > 0){
						row_div.css('border-bottom-style', 'solid').css('border-bottom-width', layout_obj['border-bottom-width']);
					}

					if(layout_obj['border-top-width'] > 0){
						row_div.css('border-bottom-style', 'solid').css('border-top-width', layout_obj['border-top-width']);
					}

					row_div.append(element_html);
				}
			});

			// TODO: Or by adding click to children...
			$(this).find(".option-label")
				.click(function(){$(this).parent().click()});

			$(this).find(":radio").parent()
				.addClass("has-option")
				.click(radio_parent_click);

			$(this).find(":checkbox").parent()
				.addClass("has-option")
				.click(checkbox_parent_click);
			$(this).find(":input").not(".spec").change(item_change);
			$(this).find(":input.spec").change(spec_change);

			$(this).find(".col:has(.cell)").addClass('cell');
			if($(this).hasClass('responsive') && $(this).hasClass('first-col-is-number')){
				$(this).find('.table').children().each(handle_first_col);
			}
		}
	);

	init_sliders();

	$(window).resize(resize);
	update_size(true);
});

function handle_first_col(index, div){
	div = $(div);
	if(div.children().length > 1){
		var first = div.children().first();
		first.addClass('desktop-only');
		var contents = $('<span>' + first.children().first().html() + ' </span>');
		contents.removeClass('content').addClass('mobile-only');
		var second = div.children().eq(1).children().first();
		second.prepend(contents);
	}
}

function parse_cells(cells){
	var stars = [];
	var sum = 0;
	$.each(cells, function(index, cell){
		if(cell["cell-width"] == "*"){
			stars.push(index);
		}
		else sum = sum + parseInt(cell["cell-width"]);
	});
	var star_width = (700 - sum) / stars.length;
	$.each(stars, function(x, cell_index){
		cells[cell_index]["cell-width"] = star_width;
	});
	return cells;
}

// TODO: $('#div-id')[0].scrollWidth >  $('#div-id').innerWidth()
function parse_element_layout(element, layout, response_html, cells, stretch_out){
	/*
	layout = layout.replace(/\[N]/, "<span class = 'content'>" + element.name + "</span>");
	layout = layout.replace(/\[(Q|T)]/, "<span class = 'content'>" + element.text + "</span>");
	layout = layout.replace(/\[X]/, response_html);
	*/
	// TODO: The name should be sent to the browser in unescaped format and be escaped here instead
	layout = layout.replace(/\[N]/, element.name);
	// TODO: The text should be sent to the browser in unescaped format and be escaped here instead
	layout = layout.replace(/\[(Q|T)]/, element.text);
	layout = layout.replace(/\[X]/, response_html);
	var curr_cell = 0;
	var parts = layout.split("[TD");
	return $.map(parts, function(content, index){
		var colspan = 1;

		// The first part cannot have closing ]
		if(index > 0){
			// Parse [TD X] where X is colspan for this cell.
			// TODO: Clean this mess up. If index > 0 it should be safe to remove the first ]
			//if(content.indexOf("]") >= 0 && (content.indexOf("[") == -1 || content.indexOf("[") < content.indexOf("]"))){
			//if(content.substr(0, content.indexOf("]")).match(/([0-9]|\s)+/)) {
			colspan = parseInt(content.substr(0, content.indexOf("]"))) || 1;
			content = content.substr(content.indexOf("]") + 1, content.length);
		}

		// Stretch out last cell if option-separator is <br> or response isn't present
		if(index == parts.length - 1 && curr_cell < cells.length - 1 && stretch_out){
			colspan = cells.length - curr_cell;
		}

		var data = '';
		if(curr_cell < cells.length){

			// Fetch alignment from first cell even if colspan > 1
			if(cells[curr_cell]["cell-alignment"] != ""){
				data += "data-align = '" + cells[curr_cell]["cell-alignment"] + "'";
			}

			// Merge widths for all cells in colspan
			var width = 0;
			for(var i = 0; i < colspan; i++){
				width += parseInt(cells[curr_cell] ? cells[curr_cell]["cell-width"] : 0);
				curr_cell++;
			}

			data += "data-width = '" + width + "px'";
		}
		return sprintf("<div class = 'col' %s><div class='content'>%s</div></div>", data, content);
	}).join("");
}

function is_break_separator(response){
	return (response == undefined || (response["option-separator"] || "<br>").toLowerCase() == '<br>')
}

function parse_response(element, response){
	var response_type = response["response-type"];
	var name = "item-" + element["item-id"];

	// TODO: Fix so second line of option label is not under input (e.g., MADRS)
	// Radio buttons and checkboxes
	if(response_type == "RD" || response_type == "CB"){
		return $.map(response.options, function(option, index){
			var str;
			var jumps = '';

			// Check jumps
			if(element["option-jumps"][index] != undefined){
				jumps = element["option-jumps"][index].join();
			}

			// Radio button
			if(response_type == "RD"){
				str = sprintf("<input type = 'radio' name = '%s' value = '%s' data-jumps = '%s'>", name, escape_html(option.value), jumps);
			}

			// Checkbox
			else{
				var cb_name = name + "_" + escape_html(option.value);
				str = sprintf("<input type = 'hidden' name = '%s' value='0'>\n" +
					"<input type = 'checkbox' name = '%s' value = '1' data-jumps = '%s'>", cb_name, cb_name, jumps);
			}

			// Trailing option label
			if(option.label != ""){
				// TODO: The labels should be sent to the browser in unescaped format and be escaped here instead
				str += sprintf("<span class='option-label %s'>&nbsp;%s</span>", is_break_separator(response) ? '' : 'mobile', option.label);
			}

			// Add specification
			if(option.specification){
				var spec_name = [name, escape_html(option.value), "spec"].join("_");
				str += "<br>" + option["specification-text"];

				// Big specification
				if(option["specification-big"]){
					str += sprintf("<br><textarea class='spec' cols='22' rows='3' name='%s'></textarea>", spec_name);
				}

				// Small specification
				else{
					str += sprintf("&nbsp;<input type='text' class='spec' name='%s'>", spec_name);
				}
			}

			// If using break_separator enclose in div with class single
			return sprintf("<div class='option %s'>%s</div>", is_break_separator(response) ? 'line' : 'cell', str);
		}).join((is_break_separator(response) ? "" : "[TD]") + "\n");
	}

	// Texts
	if(response_type == "ST" || response_type == "TX"){

		// Min-max check
		//TODO: This does not allow for only min or only max
		var check = '';
		if(response["range-min"] != null && response["range-min"] != null){
			check = sprintf("data-range-min = '%d' data-range-max = '%d'", response["range-min"], response["range-max"]);
		}

		// Regexp check
		else if(response.regexp != ""){
			check = sprintf("data-regexp = '%s'", escape_html(response.regexp));
		}

		// Check failed text
		if(check != "" && response["check-error-text"] != ""){
			check += sprintf("data-check-error = '%s'", escape_html(response["check-error-text"]));
		}

		// Small text
		if(response_type == "ST"){
			return sprintf("<input type = 'text' name = '%s' %s>", name, check);
		}

		// Large text
		else{
			return sprintf("<textarea cols='60' rows='5' name='%s' %s></textarea>", name, check);
		}
	}

	// VAS
	if(response_type == "VS"){
		return sprintf("<div class='slider-container'><div class='slider-label-left'>%s</div>" +
			"<div data-input = '%s' class = 'slider'></div>" +
			"<div class='slider-label-right'>%s</div>" +
			"<input type = 'hidden' name = '%s' value = '-1'></div>",
			response["vas-min-label"], name, response["vas-max-label"], name);
	}
}

var entity_map = {
	'&': '&amp;',
	'<': '&lt;',
	'>': '&gt;',
	'"': '&quot;',
	"'": '&#39;',
	'/': '&#x2F;',
	'`': '&#x60;',
	'=': '&#x3D;',
	'$': '&#36'
};

function escape_html (string) {
	return String(string).replace(/[&<>"'`=\/$]/g, function (s) {
		return entity_map[s];
	});
}


/***********************
 * 	SLIDER (VAS)
 ***********************/


function init_sliders(){
	$(".slider").each(function(){
		$(this)
			.slider({min: 0, max: 401})
			.bind('slidechange', function(event,ui){slider_change(this, ui)})
			.children(".ui-slider-handle").css('visibility', 'hidden');
	})
}

function slider_change(slider, ui){
	ui.handle.style.visibility = "visible";
	var input = $("input[name=" + $(slider).data('input') + "]");
	input.val(ui.value > 0 ? ui.value - 1 : 0);
	$(slider).closest('.item-div').addClass('has-data');
}


/***********************
 * INTERACTIVE BEHAVIOR
 ***********************/

function spec_change(event){
	$(event.target).prevAll(":input").first().click();
}

function radio_parent_click(event){
	var input = $(event.target).find(":radio");
	if (input.length && !input.prop("disabled")) {
		input.click();

		// TODO: The code in BASS is more complicated - why? Browser compatibility?
		//input.prop("checked", true).click();
		//item_change.call(input.get(0));
		// $('form').trigger('checkform.areYouSure');
	}
}

function checkbox_parent_click(event){
	var input = $(event.target).find(":checkbox");
	if (input.length && !input.prop("disabled")) {
		input.click();
		// $('form').trigger('checkform.areYouSure');
	}
}

function item_change(){
	var item_div = $(this).closest('.item-div');
	var has_value;
	if($(this).is(":checkbox, :radio")){
		has_value = $(this).prop("checked");
	}
	else{
		has_value = $(this).val() != "";
	}
	if($(this).is(":checkbox")){
		item_div.data("has-data", (item_div.data("has-data") || 0) + (has_value ? 1 : -1));
	}
	else {
		item_div.data("has-data", has_value);
	}

	if(item_div.data("has-data")){
		item_div.addClass('has-data');
	}
	else{
		item_div.removeClass('has-data');
	}

	handle_jumps($(this), item_div, has_value);
}

function handle_jumps(input, item_div, has_value){
	var jump_container = input.is(":radio") ? item_div : input;

	// Remove old jumps
	var affected = toggle_jumps(jump_container, -1);
	jump_container.removeData("prev-jumps");

	if(has_value && input.data('jumps')){
		jump_container.data("prev-jumps", String(input.data("jumps")).split(','));
		affected = affected.concat(toggle_jumps(jump_container, 1));
	}

	$.each(affected, function(index, jumpee){
		if(jumpee.data('jump-stack') > 0){
			jumpee.addClass('jumped-over');
			/*
			 This is to prevent disabled options from disabling other options.
			 TODO: Consider if all values should be cleared and saved if things are enabled again
			 */
			// http://stackoverflow.com/questions/7055729/onchange-event-not-fire-when-the-change-come-from-another-function
			jumpee.find(":radio:checked, :checkbox:checked").prop("checked", false).change();
			jumpee.find(":input").prop("disabled", true);
			jumpee.find(".slider").slider("disable");
		}
		else{
			jumpee.removeClass('jumped-over');
			jumpee.find(":input").prop("disabled", false);
			jumpee.find(".slider").slider("enable");
		}
	})
}

function toggle_jumps(jump_container, mod){
	var affected = [];
	$.each(jump_container.data("prev-jumps"), function(index, item_id){
		var jumpee = $("#item-" + item_id);
		if(jumpee){
			jumpee.data('jump-stack', (jumpee.data('jump-stack') || 0) + mod);
			affected.push(jumpee);
		}
	});
	return affected;
}



/***********************
 * RESPONSIVE RESIZING
 ***********************/
/*
function resize(event) {
	var width = $(window).width();
	var instrument = $('#instrument.responsive');
	if (width <= 800 && instrument.hasClass('desktop')) {
		toggle_size(instrument);
	}
	else
	if (width > 800 && instrument.hasClass('mobile')) {
		toggle_size(instrument);
	}
}
*/

function resize(event) {
	update_size();
}

function update_size(force_paint){
	if(force_paint == undefined){
		force_paint = false;
	}
	var width = $(window).width();
	var instrument = $('#instrument');
	if (width <= 800 && instrument.hasClass('responsive') && (instrument.hasClass('desktop') || force_paint)) {
		set_mobile(instrument);
		paint_instrument(instrument);
	}
	else
	if ((width > 800 || !instrument.hasClass('responsive')) && (instrument.hasClass('mobile') || force_paint)) {
		set_desktop(instrument);
		paint_instrument(instrument);
	}
}

function paint_instrument(instrument){
	if (instrument.hasClass('desktop')) {
		$(".col").each(function(){
			$(this).width($(this).data('width'));
			$(this).css('text-align', $(this).data('align'));
		})
	}
	else
	if (instrument.hasClass('mobile')) {
		$(".col").each(function(){
			$(this).css('text-align', '');
			if($(this).hasClass('.cell')){
				$(this).width('100%');
			}
			else{
				$(this).width('auto');
			}
		})
	}
}

function set_mobile(instrument){
	instrument.removeClass("desktop").addClass("mobile");
}

function set_desktop(instrument){
	instrument.removeClass("mobile").addClass("desktop");
}

function toggle_size(instrument){
	if (instrument.hasClass('desktop')) {
		set_mobile(instrument)
	}
	else
	if (instrument.hasClass('mobile')) {
		set_desktop(instrument)
	}
	paint_instrument(instrument);
}