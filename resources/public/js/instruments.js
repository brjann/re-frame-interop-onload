var message_saving = false;
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
					var row_div = $("<div style='width: 700px'></div>").appendTo(table_div);

					var layout_obj = instrument.layouts[element["layout-id"]];
					var layout = (layout_obj == undefined) ? "[T]" : layout_obj.layout;
					var response = instrument.responses[element["response-id"]];
					var response_html;
					if(element["item-id"] != undefined){
						response_html = parse_response(element, response);
					}
					else response_html = "";

					var element_html = parse_element_layout(element, layout, response_html, cells, (response == undefined || response["option-separator"].toLowerCase() == '<br>'));
					row_div.append(element_html);
				}
			})
		}
	)
});

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
	console.log(star_width);
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
		if(content.indexOf("]") >= 0 && (content.indexOf("[") == -1 || content.indexOf("[") < content.indexOf("]"))){
			colspan = parseInt(content.substr(0, content.indexOf("]"))) || 1;
			content = content.substr(content.indexOf("]") + 1, content.length);
		}

		// Stretch out last cell if option-separator is <br> or response isn't present
		if(index == parts.length - 1 && curr_cell < cells.length - 1 && stretch_out){
			colspan = cells.length - curr_cell;
		}

		// Set width and alignment of cell
		var settings = '';
		if(curr_cell < cells.length){
			// Fetch alignment from first cell if colspan > 1
			if(cells[curr_cell]["cell-alignment"] != ""){
				settings = settings + "text-align: " + cells[curr_cell]["cell-alignment"] + ";"
			}
			var width = 0;
			for(var i = 0; i < colspan; i++){
				width = width + parseInt(cells[curr_cell] ? cells[curr_cell]["cell-width"] : 0);
				curr_cell++;
			}
			settings = settings + "width: " + width + "px;";
		}
		return "<div class = 'cell' style='" + settings + "'>" + content + "</div>";
	});
}

function parse_response(element, response){
	var break_separator = response["option-separator"].toLowerCase() == "<br>";
	if(response["response-type"] == "RD"){
		return $.map(response.options, function(option){
			var str = "<input type = 'radio' name = '" + element["item-id"] + "' value = '" + escape_string(option.value) + "'>";
			if(option.label != undefined){
				str = str + " " + option.label;
			}
			if(break_separator){
				str = "<div class='option single'>" + str + " </div>";
			}
			return str;
		}).join((break_separator ? "" : "[TD]") + "\n");
	}
}

function escape_string(str){
	if(str == undefined || str == ""){
		return "";
	}
	return JSON.stringify(str).slice(1, -1);
}