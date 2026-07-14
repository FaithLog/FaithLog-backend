package com.faithlog.batch.service;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.service.CoffeeOperationClassifier;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduledPollCreationService {

	private final PollTemplateRepository pollTemplateRepository;
	private final ScheduledPollFactory scheduledPollFactory;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public ScheduledPollCreationService(
		PollTemplateRepository pollTemplateRepository,
		ScheduledPollFactory scheduledPollFactory,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.scheduledPollFactory = scheduledPollFactory;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public int createDuePolls(Instant now) {
		int createdCount = 0;
		for (PollTemplate template : pollTemplateRepository.findByIsActiveTrueAndAutoCreateEnabledTrueOrderByIdAsc()) {
			if (CoffeeOperationClassifier.isCoffeeOperation(
				template.pollType(), template.chargeGenerationType(), template.paymentCategory())) {
				continue;
			}
			if (createDuePoll(template, now)) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private boolean createDuePoll(PollTemplate template, Instant now) {
		ScheduledPollWindow window = ScheduledPollWindow.from(template, now);
		if (!window.isDue(now)) {
			return false;
		}
		NotificationLockKey lockKey = new NotificationLockKey(
			"poll-auto-create",
			template.campusId(),
			"template:" + template.id() + ":week:" + window.weekStartDate()
		);
		return notificationLockService.acquireScheduledLock(lockKey)
			.map(lease -> {
				try {
					return Boolean.TRUE.equals(transactionTemplate.execute(
						status -> scheduledPollFactory.createIfAbsent(template.id(), window)
					));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElse(false);
	}
}
