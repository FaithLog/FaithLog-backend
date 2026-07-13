package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
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
		Poll poll = pollLookupSupport.getPollInCampusForUpdate(campusId, pollId);
		if (poll.pollType() == PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		pollAccessService.requirePollAdmin(campusId, requesterId, poll.pollType());
		if (poll.status() != PollStatus.OPEN) {
			throw new BusinessException(ErrorCode.POLL_CLOSE_NOT_ALLOWED);
		}
		poll.closeAt(Instant.now());
		if (poll.pollType() == PollType.COFFEE) {
			coffeePollSettlementService.settleClosedCoffeePoll(campusId, pollId);
		}
		return pollResultAssembler.toResult(poll);
	}
}
