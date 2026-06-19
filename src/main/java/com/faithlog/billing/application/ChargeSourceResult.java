package com.faithlog.billing.application;

import com.faithlog.billing.domain.ChargeSourceType;

public record ChargeSourceResult(
	ChargeSourceType sourceType,
	Long sourceId
) {
}
