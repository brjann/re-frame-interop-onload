/*
	FINISHED:
		- Hovering
		- Completed
		- Jumps
		- Borders
		- No distance between number and question in mobile mode
		- Page breaks
		- When data input into specification, select corresponding radiobutton/checkbox
	   - Non-responsive instrument
		- Min-max
	 	- Regex
	 	- Optional items
	   - Validation
		- Scroll to top error item
	   - Handling of "empty" items
      - submission

	TODO: Fix these issues
		- Are you sure
		- Gray area does not cover whole cell in desktop mode
		- What happens if multiple radiobuttons have spec and there are values in all specs
		- Find better feedback color than green
 */


/***********************
 * INSTRUMENT RENDERING
 ***********************/
$(document).ready(function () {
   $("#instrument").each(
      function () {
         var cols = [];
         var show_name = instrument["show-name"];
         $(this).find(".instrument-show-name").html('<h2>' + show_name + '</h2>');
         $(this).addClass('instrument');

         // TODO: Remove debug - NOT SURE WHAT THIS TODO IS.
         var $responsive_toggler = $("#responsive-toggler");
         if ($responsive_toggler.length) {
            if (!instrument.responsive) {
               $responsive_toggler
                  .append('<i class="fa fa-exclamation-circle" style="color:red"></i>')
                  .prop('title', 'This instrument is not configured to be responsive and will look identical on desktop and mobile devices.')
                  .children('.mobile-button').remove();
            }
            else {
               $responsive_toggler.click(function () {
                  toggle_size($("#instrument"))
               });
            }
         }

         var instrument_div = $(this).find(".instrument-elements");

         if (instrument.classic) {
            $(this).addClass('classic');
         }

         if (instrument.responsive) {
            $(this).addClass('responsive');
         }

         if (instrument['first-col-is-number']) {
            $(this).addClass('first-col-is-number');
         }

         var page_div;
         $.each(instrument.elements, function (index, element) {

            if (element['page-break'] == 1 || index == 0) {
               page_div = $('<div class="page"></div>').appendTo(instrument_div);
            }

            if (element.cols != undefined) {
               cols = parse_cols(element.cols);
            }
            else {
               var item_attrs = '';
               var layout_obj;
               if (element["item-id"] != undefined) {
                  layout_obj = instrument.layouts[element["layout-id"]];
                  item_attrs = "class = 'item-div' id = 'item-" + element['item-id'] + "'";
               }
               else {
                  layout_obj = instrument["static-layouts"][element["layout-id"]];
               }

               var layout = layout_obj.layout || "[T]";

               // Make sure item element's layout include [X] for painting of inputs.
               if (element["item-id"] != undefined) {
                  if (layout.indexOf('[X]') == -1) {
                     return;
                  }
               }

               var row_div = $(sprintf("<div %s></div>", item_attrs)).appendTo(page_div);

               var response = instrument.responses[element["response-id"]];
               var response_html;
               if (element["item-id"] != undefined) {
                  response_html = parse_response(element, response);
                  row_div.data('response-type', response['response-type']);
                  row_div.data('optional?', element['optional?']);
               }
               else response_html = "";

               var element_html = parse_element_layout(element, layout, response_html, cols, is_break_separator(response));
               if (element_html.indexOf("<!--desktop-->") >= 0) {
                  row_div.addClass('desktop-only');
               }
               else if (element_html.indexOf("<!--mobile-->") >= 0) {
                  row_div.addClass('mobile-only');
               }

               if (layout_obj['border-bottom-width'] > 0) {
                  row_div.css('border-bottom-style', 'solid').css('border-bottom-width', layout_obj['border-bottom-width']);
               }

               if (layout_obj['border-top-width'] > 0) {
                  row_div.css('border-top-style', 'solid').css('border-top-width', layout_obj['border-top-width']);
               }

               row_div.append(element_html);
            }
         });
         init_instrument($(this));
      }
   );

   sliders_init();

   $(window).resize(resize);
   update_size(true);
});

function init_instrument($instrument) {
   $instrument.find(".option-label")
      .click(function () {
         $instrument.parent().click()
      });

   $instrument.find(":radio, :checkbox").parent()
      .addClass("has-option")
      .click(checker_parent_click);

   // Activate item change also for specs to add has-data class
   // after specification missing error
   //instrument.find(":input").not(".spec").change(item_change);
   $instrument.find(":input.spec").change(spec_change);
   $instrument.find(":input").change(item_change);

   $instrument.find(".col:has(.cell)").addClass('cell');
   if ($instrument.hasClass('responsive') && $instrument.hasClass('first-col-is-number')) {
      $instrument.find('.page').children().not('.navigator').each(handle_first_col);
   }

   var pages = $instrument.find('.page');
   pages.each(init_pages(pages, $instrument));

   $instrument.addClass('form').areYouSure();
}

function init_pages(pages, $instrument) {

   var page_count_updater = function (index) {
      if (pages.length > 1) {
         $('.page-count').text(sprintf(text_page_count, index + 1, pages.length));
      }
   };

   var show_page = function (index) {
      window.scrollTo(0, 0);
      pages.hide();
      var page = pages.eq(index);
      page.show();
      paint_instrument($instrument);
      page_count_updater(index);
      if (page.data('page-button')) {
         $('.page-button').removeClass('active');
         page.data('page-button').addClass('active');
      }
   };

   var init_page_button = function (page, index, pages) {
      var page_selector = $('#page-selector');
      if (page_selector.length) {
         var page_button = $(sprintf('<button class="btn btn-outline-danger btn-sm page-button">%s</button>', index + 1));
         if (index === 0) {
            page_button.addClass('active');
         }
         page_button.click(
            function () {
               show_page(index);
            });
         page.data('page-button', page_button);
         $('#page-selector').append(page_button);
      }
   };

   page_count_updater(0);

   return function (index) {
      var page = $(this);

      init_page_button(page, index, pages);

      var left_div = $('<div class="left"></div>');
      var right_div = $('<div class="right"></div>');
      var navigate_div = $('<div></div>');

      // Next or submit
      var right_button;
      if (index === pages.length - 1) {
         right_button = button(text_submit);
         right_button.click(function () {
            if (validate_page(page)) {
               submit_instrument(page.closest('.instrument'));
            }
         });
      }
      else {
         right_button = button(text_next);
         right_button.click(function () {
            if (validate_page(page)) {
               show_page(index + 1)
            }
         });
      }
      right_div.append(right_button);

      // Previous or nothing
      if (index > 0) {
         var left_button = button(text_previous);
         left_button.click(function () {
            show_page(index - 1);
         });
         left_div.append(left_button);
      }

      navigate_div.append(left_div, right_div);
      navigate_div.addClass('navigator');

      page.prepend(navigate_div);
      page.append(navigate_div.clone(true));

      if (index > 0) {
         page.hide();
      }
   }
}

function button(text) {
   return $('<button type="button" class="btn btn-primary">' + text + '</button>');
}

function handle_first_col(index) {
   var div = $(this);
   if (div.children().length > 1) {
      var first = div.children().first();
      first.addClass('desktop-only');
      var contents = $('<span>' + first.children().first().html() + ' </span>');
      contents.removeClass('content').addClass('mobile-only');
      var second = div.children().eq(1).children().first();
      second.prepend(contents);
   }
}

function parse_cols(cols) {
   var stars = [];
   var sum = 0;
   $.each(cols, function (index, col) {
      if (col["col-width"] == "*") {
         stars.push(index);
      }
      else sum = sum + parseInt(col["col-width"]);
   });
   var star_width = (700 - sum) / stars.length;
   $.each(stars, function (x, col_index) {
      cols[col_index]["col-width"] = star_width;
   });
   return cols;
}

// TODO: $('#div-id')[0].scrollWidth >  $('#div-id').innerWidth()
function parse_element_layout(element, layout, response_html, cols, stretch_out) {
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
   var curr_col = 0;
   var parts = layout.split("[TD");
   return $.map(parts, function (content, index) {
      var colspan = 1;

      // The first part cannot have closing ]
      if (index > 0) {
         // Parse [TD X] where X is colspan for this col.
         // TODO: Clean this mess up. If index > 0 it should be safe to remove the first ]
         //if(content.indexOf("]") >= 0 && (content.indexOf("[") == -1 || content.indexOf("[") < content.indexOf("]"))){
         //if(content.substr(0, content.indexOf("]")).match(/([0-9]|\s)+/)) {
         colspan = parseInt(content.substr(0, content.indexOf("]"))) || 1;
         content = content.substr(content.indexOf("]") + 1, content.length);
      }

      // Stretch out last col if option-separator is <br> or response isn't present
      if (index == parts.length - 1 && curr_col < cols.length - 1 && stretch_out) {
         colspan = cols.length - curr_col;
      }

      var data = '';
      if (curr_col < cols.length) {

         // Fetch alignment from first col even if colspan > 1
         if (cols[curr_col]["col-alignment"] != "") {
            data += "data-align = '" + cols[curr_col]["col-alignment"] + "'";
         }

         // Merge widths for all cols in colspan
         var width = 0;
         for (var i = 0; i < colspan; i++) {
            width += parseInt(cols[curr_col] ? cols[curr_col]["col-width"] : 0);
            curr_col++;
         }

         data += "data-width = '" + width + "px'";
      }
      return sprintf("<div class = 'col' %s><div class='content'>%s</div></div>", data, content);
   }).join("");
}

function is_break_separator(response) {
   return (response == undefined || (response["option-separator"] || "<br>").toLowerCase() == '<br>')
}

function parse_response(element, response) {
   var response_type = response["response-type"];
   var name = "item-" + element["item-id"];

   // TODO: Fix so second line of option label is not under input (e.g., MADRS)
   // Radio buttons and checkboxes
   if (response_type === "RD" || response_type === "CB") {
      return $.map(response.options, function (option, index) {
         var str;
         var jumps = '';

         // Check jumps
         if (element["option-jumps"][index] != undefined) {
            jumps = element["option-jumps"][index].join();
         }

         // Radio button
         if (response_type === "RD") {
            str = sprintf("<input type = 'radio' name = '%s' value = '%s' data-jumps = '%s' data-has-specification = '%s'>", name, escape_html(option.value), jumps, option['specification?']);
         }

         // Checkbox
         else {
            var cb_name = name + "_" + escape_html(option.value);
            str = sprintf("<input type = 'checkbox' name = '%s' value = '1' data-jumps = '%s' data-has-specification = '%s'>", cb_name, jumps, option['specification?'])
         }

         // Trailing option label
         if (option.label != "") {
            // TODO: The labels should be sent to the browser in unescaped format and be escaped here instead
            str += sprintf("<span class='option-label %s'>&nbsp;%s</span>", is_break_separator(response) ? '' : 'mobile', option.label);
         }

         // Add specification
         if (option['specification?']) {
            var spec_name = [name, escape_html(option.value), "spec"].join("_");
            str += "<br>" + option["specification-text"];

            // Big specification
            if (option["specification-big?"]) {
               str += sprintf("<br><textarea class='spec' cols='22' rows='3' name='%s'></textarea>", spec_name);
            }

            // Small specification
            else {
               str += sprintf("&nbsp;<input type='text' class='spec' name='%s'>", spec_name);
            }
         }

         // If using break_separator enclose in div with class single
         return sprintf("<div class='option %s'>%s</div>", is_break_separator(response) ? 'line' : 'cell', str);
      }).join((is_break_separator(response) ? "" : "[TD]") + "\n");
   }

   // Texts
   if (response_type == "ST" || response_type == "TX") {

      // Min-max check
      //TODO: This does not allow for only min or only max
      var check = '';
      if (response["range-min"] != null && response["range-min"] != null) {
         // %d makes both values negative if one is negative.
         check = sprintf("data-range-min = '%s' data-range-max = '%s'", response["range-min"], response["range-max"]);
      }

      // Regex check
      else if (response.regex != "") {
         check = sprintf("data-regex = '%s'", escape_html(response.regex));
      }

      // Check failed text
      if (check != "" && response["check-error-text"] != "") {
         check += sprintf("data-error-text = '%s'", escape_html(response["check-error-text"]));
      }

      // Small text
      if (response_type == "ST") {
         return sprintf("<input type = 'text' name = '%s' %s>", name, check);
      }

      // Large text
      else {
         return sprintf("<textarea cols='60' rows='5' name='%s' %s></textarea>", name, check);
      }
   }

   // VAS
   if (response_type === "VS") {
      if (instrument.responsive) {
         return sprintf("<div class='slider-container'>" +
            "<div class='slider-label-left-mobile mobile-only'>%1$s</div>" +
            "<div class='slider-label-right-mobile mobile-only'>%3$s</div>" +
            "<div class='slider-label-left desktop-only'>%1$s</div>" +
            "<div data-input = '%2$s' class = 'slider'></div>" +
            "<div class='slider-label-right desktop-only'>%3$s</div>" +
            "<input type = 'hidden' class = 'vas' name = '%2$s' value = '-1'></div>",
            response["vas-min-label"],
            name,
            response["vas-max-label"]);
      } else {
         return sprintf("<div class='slider-container'>" +
            "<div class='slider-label-left'>%1$s</div>" +
            "<div data-input = '%2$s' class = 'slider'></div>" +
            "<div class='slider-label-right'>%3$s</div>" +
            "<input type = 'hidden' class = 'vas' name = '%2$s' value = '-1'></div>",
            response["vas-min-label"],
            name,
            response["vas-max-label"]);
      }
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

function escape_html(string) {
   return String(string).replace(/[&<>"'`=\/$]/g, function (s) {
      return entity_map[s];
   });
}


/***********************
 *   SLIDER (VAS)
 ***********************/


function sliders_init() {
   $(".slider").each(function () {
      $(this)
         .slider({min: 0, max: 401})
         .bind('slidechange', function (event, ui) {
            slider_change(this, ui)
         })
         .children(".ui-slider-handle").css('visibility', 'hidden');
   })
}

function slider_change(slider, ui) {
   ui.handle.style.visibility = "visible";
   var input = $("input[name=" + $(slider).data('input') + "]");
   input.val(ui.value > 0 ? ui.value - 1 : 0);
   var item_div = $(slider).closest('.item-div');
   item_div.addClass('has-data');
   $('#error-' + item_div.prop('id')).remove();
}


/***********************
 *   FORM VALIDATION
 ***********************/

/**
 * Error types
 *
 * - Answer missing.
 * - Range/pattern error.
 * - Specification missing.
 */

function validate_page(page_div) {
   page_div.find('div.option').removeClass('alert-warning');
   page_div.find('.item-error').remove();
   var error_items = $(page_div.find('.item-div').filter(
      function () {
         var item_div = $(this);
         var error = validate_item(item_div) || '';
         if (error.length > 0) {
            item_set_error(item_div, error);
            return true;
         }
         return false;
      }));

   if (error_items.length) {
      var first_error = $('#error-' + error_items.first().prop('id'));
      $('html,body').animate({
            scrollTop: first_error.offset().top
         },
         'slow');
      return false;
   }
   return true;
}

function item_set_error(item_div, error) {
   item_div.removeClass('has-data');
   var error_div = $(sprintf('<div class = "item-error alert alert-danger" id="error-%s" role="alert">%s</div>', item_div.prop('id'), error));
   item_div.before(error_div);
}

function validate_item(item_div) {

   if (item_div.hasClass('jumped-over')) {
      return;
   }

   //var item_div = $(this);
   switch (item_div.data('response-type')) {
      case 'TX':
      case 'ST':
         return validate_text(item_div);
         break;
      case 'RD':
      case 'CB':
         return validate_checker(item_div);
      case 'VS':
         return validate_vas(item_div);
   }
}


function validate_text(item_div) {
   var input = item_div.find(':input');

   // If, for some reason, there is no input here, then return item as valid.
   if (input.length == 0) {
      return;
   }

   // Trim whitespace from value
   var val = input.val().trim();

   if (val.length == 0) {

      // Optional items can have empty value
      if (item_div.data('optional?')) {
         return;
      }

      return text_must_answer;
   }

   var min = input.data('range-min');
   var max = input.data('range-max');
   var regex = input.data('regex');
   var error_text = input.data('error-text');

   if (min != undefined || max != undefined) {
      var int_val = parseInt(val);

      if (val !== int_val.toString()) {
         int_val = NaN;
      }

      if (min != undefined && max != undefined) {
         if (int_val > max || int_val < min || isNaN(int_val)) {
            return error_text || sprintf(text_range_error, min, max);
         }
      }

      else if (min != undefined) {
         if (int_val < min || isNaN(int_val)) {
            return error_text || sprintf(text_range_error_min, min);
         }
      }

      else if (int_val > max || isNaN(int_val)) {
         return error_text || sprintf(text_range_error_max, max);
      }
   }

   if (regex != undefined) {
      try {
         var pattern = new RegExp(regex);
         if (!pattern.test(val)) {
            return error_text || text_pattern_error;
         }
      }
      catch (e) {
      }
   }
}

function validate_checker(item_div) {
   var checked = item_div.find(":input:checked");
   if (checked.length == 0) {

      // Optional items can have nothing selected
      if (item_div.data('optional?')) {
         return;
      }

      return text_must_answer;
   }

   // Look for missing specifications
   var missing_specs = checked.filter(spec_checker(item_div));

   if (missing_specs.length) {
      missing_specs.each(function () {
         spec_set_alert($(this));
      });
      return (missing_specs.length > 1) ? text_specification_missing_m : text_specification_missing_1;
   }
}

function spec_set_alert(spec) {
   spec.parents('div.option').addClass('alert-warning');
}

function spec_reset_alert(spec) {
   spec.parents('div.option').removeClass('alert-warning');
}

function spec_find(input) {
   var spec_name = input.prop('name') + (input.prop('type') == 'radio' ? '_' + input.val() : '') + '_spec';
   return input.closest('.item-div').find('[name="' + spec_name + '"]')
}

// TODO: item_div not needed
function spec_checker(item_div) {
   return function () {
      var input = $(this);
      if (input.data('has-specification')) {
         var val = spec_find(input).val() || '';
         if (val.trim().length == 0) {
            return true;
         }
      }
      return false;
   }
}

/*
function spec_checker(item_div){
	return function(){
		var input = $(this);
		if(input.data('has-specification')){
			var spec_name = input.prop('name') + '_spec';
			var val = item_div.find('[name="' + spec_name + '"]').val() || '';
			if(val.trim().length == 0){
				return true;
			}
		}
		return false;
	}
}
*/

function validate_vas(item_div) {
   var input = item_div.find(':input');
   var val = input.val();
   if (val.length == 0 || val == -1) {
      if (item_div.data('optional?')) {
         return;
      }

      return text_must_answer;
   }
}

/***********************
 * INTERACTIVE BEHAVIOR
 ***********************/

function spec_change(event) {
   var spec = $(event.target);
   spec_reset_alert(spec);
   var checker = spec.prevAll(":input").first();
   if (spec.val().length && !checker.is(':checked')) {
      // To avoid double triggering of changed event
      //checker.click();
      checker.prop('checked', true);
   }
}

function checker_parent_click(event) {
   var target = $(event.target);
   var parent;
   if (target.hasClass('has-option')) {
      parent = target;
   }
   else if (target.hasClass('option-label')) {
      parent = target.parent();
   }
   if (parent == undefined) return;

   var input = parent.find(":radio, :checkbox");
   if (input.length && !input.prop("disabled")) {
      input.click();
      // $('form').trigger('checkform.areYouSure');
   }
}

function item_change() {
   var item_div = $(this).closest('.item-div');
   $('#error-' + item_div.prop('id')).remove();
   var has_value;
   if ($(this).is(":checkbox, :radio")) {
      has_value = $(this).prop("checked");
   }
   else {
      has_value = $(this).val() != "";
   }
   if ($(this).is(":checkbox")) {
      item_div.data("has-data", (item_div.data("has-data") || 0) + (has_value ? 1 : -1));
   }
   else {
      item_div.data("has-data", has_value);
   }

   if (item_div.data("has-data")) {
      item_div.addClass('has-data');
   }
   else {
      item_div.removeClass('has-data');
   }

   handle_jumps($(this), item_div, has_value);
}

function handle_jumps(input, item_div, has_value) {
   var jump_container = input.is(":radio") ? item_div : input;

   // Remove old jumps
   var affected = toggle_jumps(jump_container, -1);
   jump_container.removeData("prev-jumps");

   if (has_value && input.data('jumps')) {
      jump_container.data("prev-jumps", String(input.data("jumps")).split(','));
      affected = affected.concat(toggle_jumps(jump_container, 1));
   }

   $.each(affected, function (index, jumpee) {
      if (jumpee.data('jump-stack') > 0) {
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
      else {
         jumpee.removeClass('jumped-over');
         jumpee.find(":input").prop("disabled", false);
         jumpee.find(".slider").slider("enable");
      }
   })
}

function toggle_jumps(jump_container, mod) {
   var affected = [];
   $.each(jump_container.data("prev-jumps"), function (index, item_id) {
      var jumpee = $("#item-" + item_id);
      $('#error-' + jumpee.prop('id')).remove();
      if (jumpee) {
         jumpee.data('jump-stack', (jumpee.data('jump-stack') || 0) + mod);
         affected.push(jumpee);
      }
   });
   return affected;
}


/***********************
 * RESPONSIVE RESIZING
 ***********************/

function resize(event) {
   update_size();
}

function update_size(force_paint) {
   if (force_paint === undefined) {
      force_paint = false;
   }
   var width = $(window).width();
   var instrument = $('#instrument');
   if (width <= 800 && instrument.hasClass('responsive') && (instrument.hasClass('desktop') || force_paint)) {
      set_mobile(instrument);
      paint_instrument(instrument);
   }
   else if ((width > 800 || !instrument.hasClass('responsive')) && (instrument.hasClass('mobile') || force_paint)) {
      set_desktop(instrument);
      paint_instrument(instrument);
   }
}

function paint_instrument(instrument) {
   if (instrument.hasClass('desktop')) {
      $(".col").each(function () {
         $(this).width($(this).data('width'));
         $(this).css('text-align', $(this).data('align'));
      });
      $('.slider').each(function () {
         // should be - 4 but 2 seems to be added
         $(this).width('');
      });
   }
   else if (instrument.hasClass('mobile')) {
      $(".col").each(function () {
         $(this).css('text-align', '');
         if ($(this).hasClass('.cell')) {
            $(this).width('100%');
         }
         else {
            $(this).width('auto');
         }
      });
      $('.slider').each(function () {
         // should be - 4 but 2 seems to be added
         $(this).width($(this).parents('.item-div').first().width() - 6);
      });
   }
}

function set_mobile(instrument) {
   instrument.removeClass("desktop").addClass("mobile");
}

function set_desktop(instrument) {
   instrument.removeClass("mobile").addClass("desktop");
}

function toggle_size(instrument) {
   if (instrument.hasClass('desktop')) {
      set_mobile(instrument)
   }
   else if (instrument.hasClass('mobile')) {
      set_desktop(instrument)
   }
   paint_instrument(instrument);
}

/***********************
 *   DATA SUBMISSION
 ***********************/

function submit_instrument(instrument_div) {
   var form = instrument_div.find('form.submitter');
   var answers = collect_answers(instrument_div);
   var items = answers[0];
   var specifications = answers[1];
   form.find(':input[name=items]').val(JSON.stringify(items));
   form.find(':input[name=specifications]').val(JSON.stringify(specifications));
   form.submit();
}

function collect_answers(instrument_div) {
   var items = {};
   var specifications = {};
   instrument_div.find('.instrument-elements .item-div :input').not('.spec').each(function () {
      if ($(this).closest("div.item-div").hasClass('jumped-over')) {
         return;
      }
      var id = this.name.substr(5, this.name.length);
      if (this.type == 'radio') {
         if ($(this).prop('checked')) {
            items[id] = $(this).val();
            if ($(this).data('has-specification')) {
               specifications[id + '_' + $(this).val()] = spec_find($(this)).val();
            }
         }
      }
      else if (this.type == 'checkbox') {
         if ($(this).prop('checked')) {
            items[id] = '1';
            if ($(this).data('has-specification')) {
               specifications[id] = spec_find($(this)).val();
            }
         }
         else {
            items[id] = '0';
         }
      }
      else if ($(this).hasClass('vas')) {
         if ($(this).val() >= 0) {
            items[id] = $(this).val();
         }
      }
      else {
         items[id] = $(this).val();
      }
   });
   return [items, specifications];
}