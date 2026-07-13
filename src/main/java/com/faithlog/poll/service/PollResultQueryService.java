package com.faithlog.poll.service;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.result.PollMissingMemberResult;
import com.faithlog.poll.service.result.PollOptionResultView;
import com.faithlog.poll.service.result.PollRespondentResult;
import com.faithlog.poll.service.result.PollResultView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollResultQueryService {

	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final PollAccessService pollAccessService;
	private final PollLookupSupport pollLookupSupport;

	public PollResultQueryService(
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		PollAccessService pollAccessService,
		PollLookupSupport pollLookupSupport
	) {
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.pollAccessService = pollAccessService;
		this.pollLookupSupport = pollLookupSupport;
	}

	@Transactional
	public PollResultView getPollResults(Long campusId, Long pollId, Long requesterId) {
		Poll poll = pollLookupSupport.getVisiblePoll(campusId, pollId, requesterId);
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		List<PollResponse> responses = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id());
		Map<Long, PollResponse> responsesById = responses.stream()
			.collect(HashMap::new, (map, response) -> map.put(response.id(), response), HashMap::putAll);
		List<PollResponseOption> responseOptions = responses.isEmpty()
			? List.of()
			: pollResponseOptionRepository.findByResponseIdIn(responses.stream().map(PollResponse::id).toList());
		Map<Long, List<PollResponseOption>> byOptionId = new HashMap<>();
		for (PollResponseOption responseOption : responseOptions) {
			byOptionId.computeIfAbsent(responseOption.optionId(), ignored -> new ArrayList<>()).add(responseOption);
		}
		Map<Long, CampusUserLookupResult> usersById = poll.isAnonymous()
			? Map.of()
			: pollAccessService.getUsers(responses.stream().map(PollResponse::userId).toList());
		List<PollOptionResultView> optionResults = options.stream()
			.map(option -> optionResult(
				poll, option, byOptionId.getOrDefault(option.id(), List.of()), responsesById, usersById
			))
			.toList();
		long targetMemberCount = campusMemberRepository.countByCampusIdAndStatus(
			campusId,
			CampusMemberStatus.ACTIVE
		);
		long respondedCount = responses.size();
		return new PollResultView(
			poll.id(), poll.campusId(), poll.title(), poll.pollType(), poll.selectionType(), poll.isAnonymous(),
			poll.status(), poll.startsAt(), poll.endsAt(), targetMemberCount, respondedCount,
			targetMemberCount - respondedCount, optionResults
		);
	}

	@Transactional(readOnly = true)
	public List<PollMissingMemberResult> getMissingMembers(Long campusId, Long pollId, Long requesterId) {
		Poll poll = pollLookupSupport.getPollInCampus(campusId, pollId);
		if (poll.pollType() == PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		pollAccessService.requirePollAdmin(campusId, requesterId, poll.pollType());
		Set<Long> respondedUserIds = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id())
			.stream()
			.map(PollResponse::userId)
			.collect(HashSet::new, HashSet::add, HashSet::addAll);
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.filter(member -> !respondedUserIds.contains(member.userId()))
			.map(member -> {
				CampusUserLookupResult user = pollAccessService.getUser(member.userId());
				return new PollMissingMemberResult(user.userId(), user.name(), user.email());
			})
			.toList();
	}

	private PollOptionResultView optionResult(
		Poll poll,
		PollOption option,
		List<PollResponseOption> responseOptions,
		Map<Long, PollResponse> responsesById,
		Map<Long, CampusUserLookupResult> usersById
	) {
		List<PollRespondentResult> respondents = poll.isAnonymous()
			? List.of()
			: responseOptions.stream()
				.map(responseOption -> responsesById.get(responseOption.responseId()))
				.filter(response -> response != null)
				.sorted(Comparator.comparing(PollResponse::id))
				.map(response -> {
					CampusUserLookupResult user = usersById.get(response.userId());
					return new PollRespondentResult(user.userId(), user.name(), user.email());
				})
				.toList();
		return new PollOptionResultView(
			option.id(), option.content(), option.sortOrder(), responseOptions.size(), respondents
		);
	}
}
