package com.faithlog.batch.service;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.poll.service.CoffeePollSettlementService;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PollAutomationService {

	public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final PollTemplateRepository pollTemplateRepository;
	private final PollRepository pollRepository;
	private final ScheduledPollFactory scheduledPollFactory;
	private final CoffeePollSettlementService coffeePollSettlementService;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public PollAutomationService(
		PollTemplateRepository pollTemplateRepository,
		PollRepository pollRepository,
		ScheduledPollFactory scheduledPollFactory,
		CoffeePollSettlementService coffeePollSettlementService,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollRepository = pollRepository;
		this.scheduledPollFactory = scheduledPollFactory;
		this.coffeePollSettlementService = coffeePollSettlementService;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public int createDuePolls(Instant now) {
		int createdCount = 0;
		for (PollTemplate template : pollTemplateRepository.findByIsActiveTrueAndAutoCreateEnabledTrueOrderByIdAsc()) {
			if (createDuePoll(template, now)) {
				createdCount++;
			}
		}
		return createdCount;
	}

	public int closeDueCoffeePolls(Instant now) {
		int closedCount = 0;
		List<Long> duePollIds = pollRepository.findByPollTypeAndStatusAndEndsAtLessThanEqualOrderByIdAsc(
			PollType.COFFEE,
			PollStatus.OPEN,
			now
		).stream().map(Poll::id).toList();
		for (Long pollId : duePollIds) {
			if (closeCoffeePoll(pollId)) {
				closedCount++;
			}
		}
		return closedCount;
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

	private boolean closeCoffeePoll(Long pollId) {
		Poll lockScope = pollRepository.findById(pollId).orElseThrow();
		NotificationLockKey lockKey = new NotificationLockKey(
			"coffee-poll-close",
			lockScope.campusId(),
			"poll:" + lockScope.id()
		);
		return notificationLockService.acquireScheduledLock(lockKey)
			.map(lease -> {
				try {
					return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
						Poll poll = pollRepository.findById(pollId).orElseThrow();
						if (poll.status() != PollStatus.OPEN) {
							return false;
						}
						poll.close();
						coffeePollSettlementService.settleClosedCoffeePoll(poll.campusId(), poll.id());
						return true;
					}));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElse(false);
	}

}
