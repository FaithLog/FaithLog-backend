package com.faithlog.poll.service.command;

import java.util.List;

public record CreateMealPollChargesCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	Long paymentAccountId,
	List<CreateMealPollChargeGroupCommand> groups
) {
}
