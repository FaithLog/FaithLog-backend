package com.faithlog.billing.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.billing.service.result.ChargeAccountResult;
import com.faithlog.billing.service.result.ChargeListItemResult;
import com.faithlog.billing.service.result.ChargeSourceResult;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargeListItemResponse(
	Long id,
	PaymentCategory paymentCategory,
	String title,
	String reason,
	int amount,
	ChargeStatus status,
	LocalDate dueDate,
	Instant paidAt,
	AccountResponse account,
	SourceResponse source
) {

	public static ChargeListItemResponse from(ChargeListItemResult result) {
		return new ChargeListItemResponse(
			result.id(),
			result.paymentCategory(),
			result.title(),
			result.reason(),
			result.amount(),
			result.status(),
			result.dueDate(),
			result.paidAt(),
			AccountResponse.from(result.account()),
			SourceResponse.from(result.source())
		);
	}

	public record AccountResponse(
		Long paymentAccountId,
		String bankName,
		String accountNumber,
		String accountHolder
	) {

		static AccountResponse from(ChargeAccountResult result) {
			return new AccountResponse(
				result.paymentAccountId(),
				result.bankName(),
				result.accountNumber(),
				result.accountHolder()
			);
		}
	}

	public record SourceResponse(
		ChargeSourceType sourceType,
		Long sourceId
	) {

		static SourceResponse from(ChargeSourceResult result) {
			return new SourceResponse(result.sourceType(), result.sourceId());
		}
	}
}
