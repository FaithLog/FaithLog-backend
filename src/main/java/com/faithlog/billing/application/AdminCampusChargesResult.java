package com.faithlog.billing.application;

import java.util.List;

public record AdminCampusChargesResult(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResult summary,
	List<AdminCampusChargeMemberResult> members
) {
}
