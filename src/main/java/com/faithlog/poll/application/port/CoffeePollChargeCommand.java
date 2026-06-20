package com.faithlog.poll.application.port;

import java.time.LocalDate;

public record CoffeePollChargeCommand(
	Long campusId,
	Long userId,
	Long paymentAccountId,
	Long pollResponseId,
	String title,
	String reason,
	int amount,
	LocalDate dueDate
) {
}
