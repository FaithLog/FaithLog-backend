package com.faithlog.poll.application;

import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.application.port.CoffeePollChargeCommand;
import com.faithlog.poll.application.port.CoffeePollChargePort;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollOption;
import com.faithlog.poll.domain.PollResponse;
import com.faithlog.poll.domain.PollResponseOption;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoffeePollSettlementService {

	private static final String COFFEE_ORDER_REASON = "컴포즈커피 주문";

	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CoffeePollChargePort coffeePollChargePort;

	public CoffeePollSettlementService(
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CoffeePollChargePort coffeePollChargePort
	) {
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.coffeePollChargePort = coffeePollChargePort;
	}

	@Transactional
	public void settleClosedCoffeePoll(Long campusId, Long pollId) {
		Poll poll = pollRepository.findById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!poll.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		if (poll.status() != PollStatus.CLOSED) {
			throw new BusinessException(ErrorCode.POLL_SETTLEMENT_NOT_CLOSED);
		}
		if (!isCoffeeSettlementTarget(poll)) {
			return;
		}

		dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COFFEE_DUTY_MISSING));

		Map<Long, PollOption> optionsById = optionsById(poll.id());
		List<PollResponse> responses = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id());
		Map<Long, List<PollResponseOption>> responseOptionsByResponseId = responseOptionsByResponseId(responses);
		for (PollResponse response : responses) {
			PollOption selectedOption = selectedOption(response, responseOptionsByResponseId, optionsById);
			coffeePollChargePort.createOrUpdateCoffeeCharge(new CoffeePollChargeCommand(
				poll.campusId(),
				response.userId(),
				poll.paymentAccountId(),
				response.id(),
				selectedOption.content(),
				COFFEE_ORDER_REASON,
				selectedOption.priceAmount(),
				null
			));
		}
	}

	private boolean isCoffeeSettlementTarget(Poll poll) {
		return poll.pollType() == PollType.COFFEE
			&& poll.chargeGenerationType() == ChargeGenerationType.OPTION_PRICE
			&& poll.paymentCategory() == PaymentCategory.COFFEE;
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
}
