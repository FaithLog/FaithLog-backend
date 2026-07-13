package com.faithlog.poll.service;

import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.command.CreateMealPollCommand;
import com.faithlog.poll.service.command.CreateMealPollOptionCommand;
import com.faithlog.poll.service.result.PollResult;
import java.time.Instant;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPollService {

	private final MealDutyAccessService mealDutyAccessService;
	private final PollRepository pollRepository;
	private final PollLookupSupport pollLookupSupport;
	private final PollOptionRepository pollOptionRepository;
	private final PollResultAssembler pollResultAssembler;
	private final Clock clock;

	public MealPollService(
		MealDutyAccessService mealDutyAccessService,
		PollRepository pollRepository,
		PollLookupSupport pollLookupSupport,
		PollOptionRepository pollOptionRepository,
		PollResultAssembler pollResultAssembler,
		Clock clock
	) {
		this.mealDutyAccessService = mealDutyAccessService;
		this.pollRepository = pollRepository;
		this.pollLookupSupport = pollLookupSupport;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResultAssembler = pollResultAssembler;
		this.clock = clock;
	}

	@Transactional
	public PollResult create(CreateMealPollCommand command) {
		mealDutyAccessService.requireActiveMealDuty(command.campusId(), command.requesterId());
		validateCreateCommand(command);
		Instant now = clock.instant();
		if (!command.endsAt().isAfter(now)) {
			throw new BusinessException(ErrorCode.POLL_INVALID_PERIOD);
		}
		Poll poll = pollRepository.save(Poll.createMeal(
			command.campusId(),
			command.title().trim(),
			command.isAnonymous(),
			command.allowUserOptionAdd(),
			now,
			command.endsAt(),
			command.requesterId()
		));
		pollOptionRepository.saveAll(command.options().stream()
			.sorted(java.util.Comparator.comparingInt(CreateMealPollOptionCommand::sortOrder))
			.map(option -> PollOption.create(poll.id(), option.content().trim(), null, 0, option.sortOrder()))
			.toList());
		return pollResultAssembler.toResult(poll);
	}

	@Transactional
	public PollResult close(Long campusId, Long pollId, Long requesterId) {
		mealDutyAccessService.requireActiveMealDuty(campusId, requesterId);
		Poll poll = findMealPollForUpdate(campusId, pollId);
		if (poll.status() != PollStatus.OPEN) {
			throw new BusinessException(ErrorCode.POLL_CLOSE_NOT_ALLOWED);
		}
		poll.closeAt(clock.instant());
		return pollResultAssembler.toResult(poll);
	}

	private Poll findMealPollForUpdate(Long campusId, Long pollId) {
		Poll poll = pollLookupSupport.getPollInCampusForUpdate(campusId, pollId);
		if (poll.pollType() != PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}

	private void validateCreateCommand(CreateMealPollCommand command) {
		if (!command.unknownFields().isEmpty()) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 투표 생성 요청에 허용되지 않은 필드가 있습니다.");
		}
		if (command.options() == null || command.options().isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		Set<String> contents = new HashSet<>();
		for (CreateMealPollOptionCommand option : command.options()) {
			if (option == null || !option.unknownFields().isEmpty() || option.content() == null || option.content().isBlank()
				|| option.content().trim().length() > 200
				|| !contents.add(option.content().trim().toLowerCase(Locale.ROOT))) {
				throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
			}
		}
	}
}
