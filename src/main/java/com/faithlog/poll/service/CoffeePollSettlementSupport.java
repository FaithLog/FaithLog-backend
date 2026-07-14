package com.faithlog.poll.service;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.port.CoffeePollChargeCommand;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CoffeePollSettlementSupport {

	private static final String COFFEE_ORDER_REASON = "컴포즈커피 주문";

	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;

	CoffeePollSettlementSupport(
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PaymentAccountRepositoryPort paymentAccountRepository
	) {
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.paymentAccountRepository = paymentAccountRepository;
	}

	SettlementContext prepare(Long campusId, Long pollId) {
		PollRepository.PollLockScope scope = pollRepository.findLockScopeById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!scope.getCampusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		if (scope.getStatus() != PollStatus.CLOSED) {
			throw new BusinessException(ErrorCode.POLL_SETTLEMENT_NOT_CLOSED);
		}
		if (!isCoffeeSettlementTarget(scope)) {
			return null;
		}

		dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.COFFEE, scope.getCreatedBy())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COFFEE_DUTY_MISSING));
		Poll poll = pollRepository.findByIdAndCampusIdForUpdate(pollId, campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (poll.status() != PollStatus.CLOSED) {
			throw new BusinessException(ErrorCode.POLL_SETTLEMENT_NOT_CLOSED);
		}
		if (!isCoffeeSettlementTarget(poll)) {
			return null;
		}
		if (poll.paymentAccountId() == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(poll.paymentAccountId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (account.isDeleted()
			|| !account.isActive()
			|| !account.campusId().equals(campusId)
			|| account.accountType() != PaymentCategory.COFFEE
			|| !poll.createdBy().equals(account.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}

		Map<Long, PollOption> optionsById = optionsById(poll.id());
		List<PollResponse> responses = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id());
		Map<Long, List<PollResponseOption>> responseOptionsByResponseId = responseOptionsByResponseId(responses);
		return new SettlementContext(poll, responses, responseOptionsByResponseId, optionsById);
	}

	CoffeePollChargeCommand chargeCommand(SettlementContext context, PollResponse response) {
		PollOption selectedOption = selectedOption(
			response,
			context.responseOptionsByResponseId(),
			context.optionsById()
		);
		return new CoffeePollChargeCommand(
			context.poll().campusId(),
			response.userId(),
			context.poll().paymentAccountId(),
			response.id(),
			selectedOption.content(),
			COFFEE_ORDER_REASON,
			selectedOption.priceAmount(),
			null
		);
	}

	private boolean isCoffeeSettlementTarget(Poll poll) {
		return poll.pollType() == PollType.COFFEE
			&& poll.chargeGenerationType() == ChargeGenerationType.OPTION_PRICE
			&& poll.paymentCategory() == PaymentCategory.COFFEE;
	}

	private boolean isCoffeeSettlementTarget(PollRepository.PollLockScope poll) {
		return poll.getPollType() == PollType.COFFEE
			&& poll.getChargeGenerationType() == ChargeGenerationType.OPTION_PRICE
			&& poll.getPaymentCategory() == PaymentCategory.COFFEE;
	}

	private Map<Long, PollOption> optionsById(Long pollId) {
		Map<Long, PollOption> optionsById = new HashMap<>();
		pollOptionRepository.findByPollIdOrderBySortOrderAsc(pollId)
			.forEach(option -> optionsById.put(option.id(), option));
		return optionsById;
	}

	private Map<Long, List<PollResponseOption>> responseOptionsByResponseId(List<PollResponse> responses) {
		if (responses.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<PollResponseOption>> responseOptionsByResponseId = new HashMap<>();
		pollResponseOptionRepository.findByResponseIdIn(responses.stream().map(PollResponse::id).toList())
			.forEach(responseOption -> responseOptionsByResponseId
				.computeIfAbsent(responseOption.responseId(), ignored -> new java.util.ArrayList<>())
				.add(responseOption));
		return responseOptionsByResponseId;
	}

	private PollOption selectedOption(
		PollResponse response,
		Map<Long, List<PollResponseOption>> responseOptionsByResponseId,
		Map<Long, PollOption> optionsById
	) {
		List<PollResponseOption> responseOptions = responseOptionsByResponseId.getOrDefault(response.id(), List.of());
		if (responseOptions.size() != 1) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT);
		}
		PollOption option = optionsById.get(responseOptions.get(0).optionId());
		if (option == null) {
			throw new BusinessException(ErrorCode.POLL_OPTION_NOT_FOUND);
		}
		return option;
	}

	record SettlementContext(
		Poll poll,
		List<PollResponse> responses,
		Map<Long, List<PollResponseOption>> responseOptionsByResponseId,
		Map<Long, PollOption> optionsById
	) {
	}
}
