package com.faithlog.billing.service.command;

import com.faithlog.billing.domain.type.ChargeSourceType;
import java.time.LocalDate;

public record CreatePenaltyChargeCommand(
	Long campusId,
	Long userId,
	ChargeSourceType sourceType,
	Long sourceId,
	String title,
	String reason,
	int amount,
	LocalDate dueDate
) {
}
