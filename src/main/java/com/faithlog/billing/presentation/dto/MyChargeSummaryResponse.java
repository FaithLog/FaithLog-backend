package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.ChargeCategorySummaryResult;
import com.faithlog.billing.application.MyChargeSummaryResult;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;

public record MyChargeSummaryResponse(
	Long campusId,
	String campusName,
	String region,
	Long userId,
	String name,
	int totalPaidAmount,
	int monthlyPaidAmount,
	int monthlyUnpaidAmount,
	int monthlyTotalChargeAmount,
	List<CategorySummaryResponse> monthlyByCategory
) {

	public static MyChargeSummaryResponse from(MyChargeSummaryResult result) {
		return new MyChargeSummaryResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			result.userId(),
			result.name(),
			result.totalPaidAmount(),
			result.monthlyPaidAmount(),
			result.monthlyUnpaidAmount(),
			result.monthlyTotalChargeAmount(),
			result.monthlyByCategory().stream().map(CategorySummaryResponse::from).toList()
		);
	}

	public record CategorySummaryResponse(
		PaymentCategory paymentCategory,
		int paidAmount,
		int unpaidAmount,
		int totalAmount
	) {

		static CategorySummaryResponse from(ChargeCategorySummaryResult result) {
			return new CategorySummaryResponse(
				result.paymentCategory(),
				result.paidAmount(),
				result.unpaidAmount(),
				result.totalAmount()
			);
		}
	}
}
