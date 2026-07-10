package com.faithlog.billing.service.result;

import com.faithlog.billing.domain.type.ChargeSourceType;

public record ChargeSourceResult(
	ChargeSourceType sourceType,
	Long sourceId
) {
}
