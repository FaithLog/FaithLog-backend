package com.faithlog.billing.service.result;

import java.util.List;

public record AdminCampusChargesResult(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResult summary,
	List<AdminCampusChargeMemberResult> members
) {
}
