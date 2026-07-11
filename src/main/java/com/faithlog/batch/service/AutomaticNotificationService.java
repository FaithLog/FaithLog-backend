package com.faithlog.batch.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AutomaticNotificationService {

	private final DevotionMissingNotificationService devotionMissingNotificationService;
	private final PollMissingNotificationService pollMissingNotificationService;
	private final PaymentUnpaidNotificationService paymentUnpaidNotificationService;

	public AutomaticNotificationService(
		DevotionMissingNotificationService devotionMissingNotificationService,
		PollMissingNotificationService pollMissingNotificationService,
		PaymentUnpaidNotificationService paymentUnpaidNotificationService
	) {
		this.devotionMissingNotificationService = devotionMissingNotificationService;
		this.pollMissingNotificationService = pollMissingNotificationService;
		this.paymentUnpaidNotificationService = paymentUnpaidNotificationService;
	}

	public int sendDevotionMissingReminders(Instant now) {
		return devotionMissingNotificationService.sendDevotionMissingReminders(now);
	}

	public int sendPollMissingReminders(Instant now) {
		return pollMissingNotificationService.sendPollMissingReminders(now);
	}

	public int sendPaymentUnpaidReminders(Instant now) {
		return paymentUnpaidNotificationService.sendPaymentUnpaidReminders(now);
	}
}
