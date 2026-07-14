package com.faithlog.batch.service;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.CoffeePollSettlementCommandService;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
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
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final TransactionTemplate transactionTemplate;

	public DueCoffeePollClosureService(
		PollRepository pollRepository,
		CoffeePollSettlementCommandService coffeePollSettlementCommandService,
		NotificationLockService notificationLockService,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PlatformTransactionManager transactionManager
	) {
		this.pollRepository = pollRepository;
		this.coffeePollSettlementCommandService = coffeePollSettlementCommandService;
		this.notificationLockService = notificationLockService;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
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
		PollRepository.PollLockScope lockScope = pollRepository.findLockScopeById(pollId).orElseThrow();
		NotificationLockKey lockKey = new NotificationLockKey(
			"coffee-poll-close",
			lockScope.getCampusId(),
			"poll:" + pollId
		);
		return notificationLockService.acquireScheduledLock(lockKey)
			.map(lease -> {
				try {
					return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
						dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
							lockScope.getCampusId(), DutyType.COFFEE, lockScope.getCreatedBy()
						).orElseThrow(() -> new BusinessException(ErrorCode.POLL_COFFEE_DUTY_MISSING));
						Poll poll = pollRepository.findByIdAndCampusIdForUpdate(pollId, lockScope.getCampusId()).orElseThrow();
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
