/**
 * @depends {nrs.js}
 * @depends {nrs.modals.js}
 */
var NRS = (function(NRS, $, undefined) {
	NRS.showRawTransactionModal = function(transaction) {
		$("#raw_transaction_modal_unsigned_transaction_bytes").val(transaction.unsignedTransactionBytes);
		$("#raw_transaction_modal_transaction_bytes").val(transaction.transactionBytes);

		if (transaction.fullHash) {
			$("#raw_transaction_modal_full_hash").val(transaction.fullHash);
			$("#raw_transaction_modal_full_hash_container").show();
		} else {
			$("#raw_transaction_modal_full_hash_container").hide();
		}

		if (transaction.signatureHash) {
			$("#raw_transaction_modal_signature_hash").val(transaction.signatureHash);
			$("#raw_transaction_modal_signature_hash_container").show();
		} else {
			$("#raw_transaction_modal_signature_hash_container").hide();
		}

		$("#raw_transaction_modal").modal("show");
	}

	NRS.initAdvancedModalFormValues = function($modal) {
		$(".pending_number_accounts_group").find("input[name=phasingQuorum]").val(1);

		var context = {
			labelText: "Finish Height",
			labelI18n: "finish_height",
			helpI18n: "approve_transaction_finish_height_help",
			inputName: "phasingFinishHeight",
			initBlockHeight: NRS.lastBlockHeight + 7000,
			changeHeightBlocks: 500
		}
		var $elems = NRS.initModalUIElement($modal, '.pending_finish_height_group', 'block_height_modal_ui_element', context);
		$elems.find('input').prop("disabled", true);

		context = {
			labelText: "Asset",
			labelI18n: "asset",
			inputIdName: "phasingHolding",
			inputDecimalsName: "phasingHoldingDecimals",
			helpI18n: "add_asset_modal_help"
		}
		$elems = NRS.initModalUIElement($modal, '', 'add_asset_modal_ui_element', context);
		$elems.find('input').prop("disabled", true);

		context = {
			labelText: "Currency",
			labelI18n: "currency",
			inputCodeName: "phasingHoldingCurrencyCode",
			inputIdName: "phasingHolding",
			inputDecimalsName: "phasingHoldingDecimals",
			helpI18n: "add_currency_modal_help"
		}
		$elems = NRS.initModalUIElement($modal, '', 'add_currency_modal_ui_element', context);
		$elems.find('input').prop("disabled", true);
	}

	$("#transaction_operations_modal").on("show.bs.modal", function(e) {
		$(this).find(".output_table tbody").empty();
		$(this).find(".output").hide();

		$(this).find(".tab_content:first").show();
		$("#transaction_operations_modal_button").text($.t("broadcast")).data("resetText", $.t("broadcast")).data("form", "broadcast_transaction_form");
	});

	$("#transaction_operations_modal").on("hidden.bs.modal", function(e) {
		$(this).find(".tab_content").hide();
		$(this).find("ul.nav li.active").removeClass("active");
		$(this).find("ul.nav li:first").addClass("active");

		$(this).find(".output_table tbody").empty();
		$(this).find(".output").hide();
	});

	$("#transaction_operations_modal ul.nav li").click(function(e) {
		e.preventDefault();

		var tab = $(this).data("tab");

		$(this).siblings().removeClass("active");
		$(this).addClass("active");

		$(this).closest(".modal").find(".tab_content").hide();

		if (tab == "broadcast_transaction") {
			$("#transaction_operations_modal_button").text($.t("broadcast")).data("resetText", $.t("broadcast")).data("form", "broadcast_transaction_form");
		} else if (tab == "parse_transaction") {
			$("#transaction_operations_modal_button").text($.t("parse_transaction_bytes")).data("resetText", $.t("parse_transaction_bytes")).data("form", "parse_transaction_form");
		} else {
			$("#transaction_operations_modal_button").text($.t("calculate_full_hash")).data("resetText", $.t("calculate_full_hash")).data("form", "calculate_full_hash_form");
		}

		$("#transaction_operations_modal_" + tab).show();
	});

	NRS.forms.broadcastTransactionComplete = function(response, data) {
		$("#parse_transaction_form").find(".error_message").hide();
		$("#transaction_operations_modal").modal("hide");
	}

	NRS.forms.parseTransactionComplete = function(response, data) {
		$("#parse_transaction_form").find(".error_message").hide();
		$("#parse_transaction_output_table tbody").empty().append(NRS.createInfoTable(response, true));
		$("#parse_transaction_output").show();
	}

	NRS.forms.parseTransactionError = function() {
		$("#parse_transaction_output_table tbody").empty();
		$("#parse_transaction_output").hide();
	}

	NRS.forms.calculateFullHashComplete = function(response, data) {
		$("#calculate_full_hash_form").find(".error_message").hide();
		$("#calculate_full_hash_output_table tbody").empty().append(NRS.createInfoTable(response, true));
		$("#calculate_full_hash_output").show();
	}

	NRS.forms.calculateFullHashError = function() {
		$("#calculate_full_hash_output_table tbody").empty();
		$("#calculate_full_hash_output").hide();
	}

	return NRS;
}(NRS || {}, jQuery));