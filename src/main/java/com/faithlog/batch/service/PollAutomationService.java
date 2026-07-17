package com.faithlog.batch.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PollAutomationService {

	public static final java.time.ZoneId SEOUL_ZONE = BatchTimeZone.SEOUL_ZONE;

	private final ScheduledPollCreationService scheduledPollCreationService;
	private final DueCoffeePollClosureService dueCoffeePollClosureService;

	public PollAutomationService(
		ScheduledPollCreationService scheduledPollCreationService,
		DueCoffeePollClosureService dueCoffeePollClosureService
	) {
		this.scheduledPollCreationService = scheduledPollCreationService;
		this.dueCoffeePollClosureService = dueCoffeePollClosureService;
	}

	public int createDuePolls(Instant now) {
		return scheduledPollCreationService.createDuePolls(now);
	}

	public int closeDueCoffeePolls(Instant now) {
		return dueCoffeePollClosureService.closeDueCoffeePolls(now);
	}
}
