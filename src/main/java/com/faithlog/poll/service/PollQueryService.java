package com.faithlog.poll.service;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.result.PollDetailResult;
import com.faithlog.poll.service.result.PollListItemResult;
import com.faithlog.poll.service.result.PollResponseResult;
import com.faithlog.poll.service.result.PollResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollQueryService {

	private final PollRepository pollRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final PollAccessService pollAccessService;
	private final PollStatusSynchronizer pollStatusSynchronizer;
	private final PollLookupSupport pollLookupSupport;
	private final PollResultAssembler pollResultAssembler;

	public PollQueryService(
		PollRepository pollRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		PollAccessService pollAccessService,
		PollStatusSynchronizer pollStatusSynchronizer,
		PollLookupSupport pollLookupSupport,
		PollResultAssembler pollResultAssembler
	) {
		this.pollRepository = pollRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.pollAccessService = pollAccessService;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
		this.pollLookupSupport = pollLookupSupport;
		this.pollResultAssembler = pollResultAssembler;
	}

	@Transactional
	public List<PollListItemResult> listPolls(Long campusId, Long requesterId) {
		pollAccessService.requirePollReader(campusId, requesterId);
		boolean adminWindow = pollAccessService.hasAdminVisibility(campusId, requesterId);
		List<Poll> campusPolls = pollRepository.findByCampusIdOrderByIdDesc(campusId);
		campusPolls.forEach(pollStatusSynchronizer::openScheduledPollIfCurrent);
		List<Poll> visiblePolls = campusPolls.stream()
			.filter(poll -> pollStatusSynchronizer.isVisibleInWindow(poll, adminWindow))
			.toList();
		if (visiblePolls.isEmpty()) {
			return List.of();
		}
		boolean hasCoffeePoll = visiblePolls.stream().anyMatch(poll -> CoffeeOperationClassifier.isCoffeeOperation(
			poll.pollType(), poll.chargeGenerationType(), poll.paymentCategory()));
		boolean hasMealPoll = visiblePolls.stream().anyMatch(poll -> poll.pollType() == PollType.MEAL);
		boolean activeCoffeeDuty = hasCoffeePoll && pollAccessService.isActiveCoffeeDuty(campusId, requesterId);
		boolean activeMealDuty = hasMealPoll && pollAccessService.isActiveMealDuty(campusId, requesterId);
		Set<Long> respondedPollIds = pollResponseRepository.findByPollIdInAndUserId(
				visiblePolls.stream().map(Poll::id).toList(),
				requesterId
			)
			.stream()
			.map(PollResponse::pollId)
			.collect(HashSet::new, HashSet::add, HashSet::addAll);
		return visiblePolls.stream()
			.map(poll -> PollListItemResult.of(
				poll,
				respondedPollIds.contains(poll.id()),
				isManageableByRequester(poll, requesterId, adminWindow, activeCoffeeDuty, activeMealDuty)
			))
			.toList();
	}

	@Transactional
	public PollResult getPoll(Long campusId, Long pollId, Long requesterId) {
		return pollResultAssembler.toResult(pollLookupSupport.getVisiblePoll(campusId, pollId, requesterId));
	}

	@Transactional
	public PollDetailResult getPollDetail(Long campusId, Long pollId, Long requesterId) {
		PollLookupSupport.VisiblePollAccess access = pollLookupSupport.getVisiblePollWithAccess(
			campusId, pollId, requesterId);
		Poll poll = access.poll();
		PollResponseResult myResponse = pollResponseRepository.findByPollIdAndUserId(poll.id(), requesterId)
			.map(response -> PollResponseResult.of(response, optionIdsForResponse(response.id())))
			.orElse(null);
		boolean coffeeOperation = CoffeeOperationClassifier.isCoffeeOperation(
			poll.pollType(), poll.chargeGenerationType(), poll.paymentCategory());
		boolean manageableByMe = isManageableByRequester(poll, requesterId, access.adminVisibility(),
			coffeeOperation && pollAccessService.isActiveCoffeeDuty(campusId, requesterId),
			poll.pollType() == PollType.MEAL && pollAccessService.isActiveMealDuty(campusId, requesterId));
		return new PollDetailResult(pollResultAssembler.toResult(poll), myResponse, manageableByMe);
	}

	private boolean isManageableByRequester(
		Poll poll,
		Long requesterId,
		boolean adminVisibility,
		boolean activeCoffeeDuty,
		boolean activeMealDuty
	) {
		if (CoffeeOperationClassifier.isCoffeeOperation(
			poll.pollType(), poll.chargeGenerationType(), poll.paymentCategory()
		)) {
			return CoffeeOperationClassifier.isConsistentConfiguration(
				poll.pollType(), poll.chargeGenerationType(), poll.paymentCategory()
			) && activeCoffeeDuty && requesterId.equals(poll.createdBy());
		}
		if (poll.pollType() == PollType.MEAL) {
			return activeMealDuty;
		}
		return adminVisibility;
	}

	private List<Long> optionIdsForResponse(Long responseId) {
		return pollResponseOptionRepository.findByResponseIdOrderByIdAsc(responseId)
			.stream()
			.map(com.faithlog.poll.domain.entity.PollResponseOption::optionId)
			.toList();
	}
}
