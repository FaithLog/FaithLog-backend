package com.faithlog.poll.service;

import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.MealPollChargeGroup;
import com.faithlog.poll.domain.entity.MealPollSettlement;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.MealPollOptionChargeStatus;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.MealPollChargeGroupRepository;
import com.faithlog.poll.infrastructure.repository.MealPollSettlementRepository;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.result.MealPollManagementDetailResult;
import com.faithlog.poll.service.result.MealPollManagementListItemResult;
import com.faithlog.poll.service.result.MealPollManagementOptionResult;
import com.faithlog.poll.service.result.MealPollOptionChargeResult;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPollManagementQueryService {

	private final MealDutyAccessService mealDutyAccessService;
	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final MealPollSettlementRepository settlementRepository;
	private final MealPollChargeGroupRepository chargeGroupRepository;
	private final Clock clock;

	public MealPollManagementQueryService(
		MealDutyAccessService mealDutyAccessService,
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		MealPollSettlementRepository settlementRepository,
		MealPollChargeGroupRepository chargeGroupRepository,
		Clock clock
	) {
		this.mealDutyAccessService = mealDutyAccessService;
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.settlementRepository = settlementRepository;
		this.chargeGroupRepository = chargeGroupRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<MealPollManagementListItemResult> list(
		Long campusId,
		Long requesterId,
		PollStatus status,
		boolean includeArchived,
		Pageable pageable
	) {
		mealDutyAccessService.requireActiveMealDuty(campusId, requesterId);
		Instant closedCutoff = Instant.now(clock).minus(90, ChronoUnit.DAYS);
		Page<Poll> polls = pollRepository.searchManagementPolls(
			campusId,
			PollType.MEAL,
			status,
			includeArchived,
			PollStatus.CLOSED,
			closedCutoff,
			pageable
		);
		Set<Long> chargedPollIds = settlementRepository.findByPollIdIn(polls.getContent().stream().map(Poll::id).toList())
			.stream().map(MealPollSettlement::pollId).collect(Collectors.toSet());
		return polls.map(poll -> MealPollManagementListItemResult.of(poll, chargedPollIds.contains(poll.id())));
	}

	@Transactional(readOnly = true)
	public MealPollManagementDetailResult detail(Long campusId, Long pollId, Long requesterId) {
		mealDutyAccessService.requireActiveMealDuty(campusId, requesterId);
		Poll poll = pollRepository.findByIdAndCampusId(pollId, campusId)
			.filter(candidate -> candidate.pollType() == PollType.MEAL)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		Map<Long, Integer> responseCounts = responseCounts(poll.id());
		MealPollSettlement settlement = settlementRepository.findByPollId(poll.id()).orElse(null);
		Map<Long, MealPollChargeGroup> groups = settlement == null
			? Map.of()
			: chargeGroupRepository.findBySettlementIdOrderByIdAsc(settlement.id()).stream()
				.collect(Collectors.toMap(MealPollChargeGroup::optionId, group -> group));
		boolean chargedByMe = settlement != null && settlement.chargedByUserId().equals(requesterId);
		return new MealPollManagementDetailResult(
			poll.id(), poll.campusId(), poll.title(), poll.pollType(), poll.selectionType(), poll.isAnonymous(),
			poll.allowUserOptionAdd(), poll.startsAt(), poll.endsAt(), poll.status(),
			options.stream().map(option -> new MealPollManagementOptionResult(
				option.id(), option.content(), responseCounts.getOrDefault(option.id(), 0), option.userAdded(),
				chargeResult(groups.get(option.id()), settlement, chargedByMe)
			)).toList()
		);
	}

	private Map<Long, Integer> responseCounts(Long pollId) {
		List<Long> responseIds = pollResponseRepository.findByPollIdOrderByIdAsc(pollId).stream()
			.map(com.faithlog.poll.domain.entity.PollResponse::id).toList();
		if (responseIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> counts = new HashMap<>();
		for (PollResponseOption option : pollResponseOptionRepository.findByResponseIdIn(responseIds)) {
			counts.merge(option.optionId(), 1, Integer::sum);
		}
		return counts;
	}

	private MealPollOptionChargeResult chargeResult(
		MealPollChargeGroup group,
		MealPollSettlement settlement,
		boolean chargedByMe
	) {
		if (group == null || settlement == null) {
			return MealPollOptionChargeResult.notCharged();
		}
		return new MealPollOptionChargeResult(
			MealPollOptionChargeStatus.CHARGED,
			group.calculationType(), group.enteredAmount(), group.amountPerMember(),
			group.requestedTotalAmount(), group.actualTotalAmount(), group.roundingAdjustment(),
			chargedByMe ? settlement.paymentAccountId() : null,
			chargedByMe,
			settlement.chargedAt()
		);
	}
}
