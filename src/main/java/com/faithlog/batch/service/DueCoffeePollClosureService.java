package com.faithlog.batch.service;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.CoffeePollSettlementCommandService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DueCoffeePollClosureService {

	private final PollRepository pollRepository;
	private final CoffeePollSettlementCommandService coffeePollSettlementCommandService;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public DueCoffeePollClosureService(
		PollRepository pollRepository,
		CoffeePollSettlementCommandService coffeePollSettlementCommandService,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.pollRepository = pollRepository;
		this.coffeePollSettlementCommandService = coffeePollSettlementCommandService;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
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
						coffeePollSettlementCommandService.settleClosedCoffeePoll(poll.campusId(), poll.id());
						return true;
					}));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElse(false);
	}
}
