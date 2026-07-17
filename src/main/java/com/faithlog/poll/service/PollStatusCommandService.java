package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.result.PollResult;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollStatusCommandService {

	private final PollLookupSupport pollLookupSupport;
	private final PollAccessService pollAccessService;
	private final CoffeePollSettlementService coffeePollSettlementService;
	private final PollResultAssembler pollResultAssembler;

	public PollStatusCommandService(
		PollLookupSupport pollLookupSupport,
		PollAccessService pollAccessService,
		CoffeePollSettlementService coffeePollSettlementService,
		PollResultAssembler pollResultAssembler
	) {
		this.pollLookupSupport = pollLookupSupport;
		this.pollAccessService = pollAccessService;
		this.coffeePollSettlementService = coffeePollSettlementService;
		this.pollResultAssembler = pollResultAssembler;
	}

	@Transactional
	public PollResult closePoll(Long campusId, Long pollId, Long requesterId) {
		PollRepository.PollLockScope pollSnapshot = pollLookupSupport.getPollLockScopeInCampus(campusId, pollId);
		boolean coffeeOperation = CoffeeOperationClassifier.isCoffeeOperation(
			pollSnapshot.getPollType(), pollSnapshot.getChargeGenerationType(), pollSnapshot.getPaymentCategory());
		if (coffeeOperation) {
			pollAccessService.requireCoffeePollOwnerForUpdate(campusId, requesterId, pollSnapshot);
		} else if (pollSnapshot.getPollType() == PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		} else {
			pollAccessService.requirePollAdmin(campusId, requesterId, pollSnapshot.getPollType());
		}
		Poll poll = pollLookupSupport.getPollInCampusForUpdate(campusId, pollId);
		CoffeeOperationClassifier.requireConsistentConfiguration(
			poll.pollType(), poll.chargeGenerationType(), poll.paymentCategory());
		if (poll.status() != PollStatus.OPEN) {
			throw new BusinessException(ErrorCode.POLL_CLOSE_NOT_ALLOWED);
		}
		poll.closeAt(Instant.now());
		if (coffeeOperation) {
			coffeePollSettlementService.settleClosedCoffeePoll(campusId, pollId);
		}
		return pollResultAssembler.toResult(poll);
	}
}
