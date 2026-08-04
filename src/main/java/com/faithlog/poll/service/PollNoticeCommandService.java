package com.faithlog.poll.service;

import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.command.UpdatePollNoticeCommand;
import com.faithlog.poll.service.result.PollResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollNoticeCommandService {

	private final PollLookupSupport pollLookupSupport;
	private final PollAccessService pollAccessService;
	private final MealDutyAccessService mealDutyAccessService;
	private final PollResultAssembler pollResultAssembler;
	private final PollImageAttachmentService imageAttachmentService;

	public PollNoticeCommandService(
		PollLookupSupport pollLookupSupport,
		PollAccessService pollAccessService,
		MealDutyAccessService mealDutyAccessService,
		PollResultAssembler pollResultAssembler,
		PollImageAttachmentService imageAttachmentService
	) {
		this.pollLookupSupport = pollLookupSupport;
		this.pollAccessService = pollAccessService;
		this.mealDutyAccessService = mealDutyAccessService;
		this.pollResultAssembler = pollResultAssembler;
		this.imageAttachmentService = imageAttachmentService;
	}

	@Transactional
	public PollResult updateGeneralPoll(UpdatePollNoticeCommand command) {
		PollRepository.PollLockScope scope = pollLookupSupport.getPollLockScopeInCampus(
			command.campusId(), command.pollId());
		if (scope.getPollType() == PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		if (CoffeeOperationClassifier.isCoffeeOperation(
			scope.getPollType(), scope.getChargeGenerationType(), scope.getPaymentCategory())) {
			pollAccessService.requireCoffeePollOwnerForUpdate(command.campusId(), command.requesterId(), scope);
		} else {
			pollAccessService.requirePollAdmin(command.campusId(), command.requesterId(), scope.getPollType());
		}
		return updateLocked(command);
	}

	@Transactional
	public PollResult updateMealPoll(UpdatePollNoticeCommand command) {
		mealDutyAccessService.requireActiveMealDutyForUpdate(command.campusId(), command.requesterId());
		Poll poll = pollLookupSupport.getPollInCampusForUpdate(command.campusId(), command.pollId());
		if (poll.pollType() != PollType.MEAL) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		updateContent(poll, command);
		imageAttachmentService.replace(poll.id(), command.campusId(), command.requesterId(), command.imageAssetIds());
		return pollResultAssembler.toResult(poll);
	}

	private PollResult updateLocked(UpdatePollNoticeCommand command) {
		Poll poll = pollLookupSupport.getPollInCampusForUpdate(command.campusId(), command.pollId());
		updateContent(poll, command);
		imageAttachmentService.replace(poll.id(), command.campusId(), command.requesterId(), command.imageAssetIds());
		return pollResultAssembler.toResult(poll);
	}

	private void updateContent(Poll poll, UpdatePollNoticeCommand command) {
		try {
			poll.updateTitleAndNotice(command.title(), command.notice());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
	}
}
