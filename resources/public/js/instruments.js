/*
	FINISHED:
		- Hovering
	TODO:
		- Page breaks
		- Jumps
		- Min-max
		- Regexp
		- Optional items
		- Borders
		- Completed
		- When data input into specification, select corresponding radiobutton/checkbox
		- Validation/submission
		- Are you sure
 */

$(document).ready(function(){
	$("#instrument").each(
		function(){
			var cells = [];
			var show_name = instrument["show-name"];
			$("#instrument-show-name").text(show_name);
			var instrument_div = $("#instrument-container");
			var table_div = $("<div></div>").appendTo(instrument_div);
			$.each(instrument.elements, function(index, element){
				if(element.cells != undefined){
					cells = parse_cells(element.cells);
				}
				else{
					var row_div = $("<div class = 'item-div' style='width: 700px'></div>").appendTo(table_div);

					var layout_obj = instrument.layouts[element["layout-id"]];
					// TODO: This probably allows empty items to get a div
					var layout = (layout_obj == undefined) ? "[T]" : layout_obj.layout;
					var response = instrument.responses[element["response-id"]];
					var response_html;
					if(element["item-id"] != undefined){
						response_html = parse_response(element, response);
					}
					else response_html = "";

					var element_html = parse_element_layout(element, layout, response_html, cells, is_break_separator(response));
					row_div.append(element_html);
				}
			});

			$(this).find(":radio").parent()
				.addClass("has-option")
				.click(radio_parent_click);

			$(this).find(":checkbox").parent()
				.addClass("has-option")
				.click(checkbox_parent_click);
			$(this).find(":input").change(item_change);
		}
	);

	init_sliders();
});

function radio_parent_click(event){
	var input = $(event.target).find(":radio");
	if (input.length && !input.prop("disabled")) {
		input.prop("checked", true).click();
		item_change.call(input.get(0));
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
}

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
	layout = layout.replace(/\[N]/, element.name);
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
				//}
			//}
		}

		// Stretch out last cell if option-separator is <br> or response isn't present
		if(index == parts.length - 1 && curr_cell < cells.length - 1 && stretch_out){
			colspan = cells.length - curr_cell;
		}

		// Set width and alignment of cell
		var settings = '';
		if(curr_cell < cells.length){

			// Fetch alignment from first cell even if colspan > 1
			if(cells[curr_cell]["cell-alignment"] != ""){
				settings += "text-align: " + cells[curr_cell]["cell-alignment"] + ";"
			}

			// Merge widths for all cells in colspan
			var width = 0;
			for(var i = 0; i < colspan; i++){
				width += parseInt(cells[curr_cell] ? cells[curr_cell]["cell-width"] : 0);
				curr_cell++;
			}

			settings += "width: " + width + "px;";
		}
		return sprintf("<div class = 'cell' style='%s'>%s</div>", settings, content);
	});
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
				str = sprintf("<input type = 'radio' name = '%s' value = '%s' data-jumps = '%s'>", name, escape_string(option.value), jumps);
			}

			// Checkbox
			else{
				var cb_name = name + "_" + escape_string(option.value);
				str = sprintf("<input type = 'hidden' name = '%s' value='0'>\n" +
					"<input type = 'checkbox' name = '%s' value = '1' data-jumps = '%s'>", cb_name, cb_name, jumps);
			}

			// Trailing option label
			if(option.label != ""){
				str += " " + option.label;
			}

			// Add specification
			if(option.specification){
				var spec_name = [name, escape_string(option.value), "spec"].join("_");
				str += "<br>" + option["specification-text"];

				// Big specification
				if(option["specification-big"]){
					str += sprintf("<br><textarea cols='22' rows='3' name='%s'></textarea>", spec_name);
				}

				// Small specification
				else{
					str += sprintf("&nbsp;<input type='text' name='%s'>", spec_name);
				}
			}

			// If using break_separator enclose in div with class single
			if(is_break_separator(response)){
				str = sprintf("<div class='option single'>%s</div>", str);
			}
			return str;
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
			check = sprintf("data-regexp = '%s'", escapeHtml(response.regexp));
		}

		// Check failed text
		if(check != "" && response["check-error-text"] != ""){
			check += sprintf("data-check-error = '%s'", escape_string(response["check-error-text"]));
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

function escape_string(str){
	if(str == undefined || str == ""){
		return "";
	}
	return JSON.stringify(str).slice(1, -1);
}

var entityMap = {
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

function escapeHtml (string) {
	return String(string).replace(/[&<>"'`=\/$]/g, function (s) {
		return entityMap[s];
	});
}