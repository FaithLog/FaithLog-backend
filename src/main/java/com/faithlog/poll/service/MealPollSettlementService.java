package com.faithlog.poll.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.ChargeCreationService;
import com.faithlog.billing.service.command.CreateMealChargeCommand;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.MealPollChargeGroup;
import com.faithlog.poll.domain.entity.MealPollSettlement;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.MealPollChargeGroupRepository;
import com.faithlog.poll.infrastructure.repository.MealPollSettlementRepository;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.command.CreateMealPollChargeGroupCommand;
import com.faithlog.poll.service.command.CreateMealPollChargesCommand;
import com.faithlog.poll.service.result.MealPollChargeGroupResult;
import com.faithlog.poll.service.result.MealPollSettlementResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPollSettlementService {

	private final MealDutyAccessService mealDutyAccessService;
	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final MealPollSettlementRepository settlementRepository;
	private final MealPollChargeGroupRepository chargeGroupRepository;
	private final ChargeCreationService chargeCreationService;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final MealChargeCalculator mealChargeCalculator;

	public MealPollSettlementService(
		MealDutyAccessService mealDutyAccessService,
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		MealPollSettlementRepository settlementRepository,
		MealPollChargeGroupRepository chargeGroupRepository,
		ChargeCreationService chargeCreationService,
		ChargeItemRepositoryPort chargeItemRepository,
		MealChargeCalculator mealChargeCalculator
	) {
		this.mealDutyAccessService = mealDutyAccessService;
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.settlementRepository = settlementRepository;
		this.chargeGroupRepository = chargeGroupRepository;
		this.chargeCreationService = chargeCreationService;
		this.chargeItemRepository = chargeItemRepository;
		this.mealChargeCalculator = mealChargeCalculator;
	}

	@Transactional
	public MealPollSettlementResult settle(CreateMealPollChargesCommand command) {
		mealDutyAccessService.requireActiveMealDuty(command.campusId(), command.requesterId());
		Poll poll = pollRepository.findByIdAndCampusIdForUpdate(command.pollId(), command.campusId())
			.filter(candidate -> candidate.pollType() == PollType.MEAL)
			.filter(candidate -> candidate.selectionType() == SelectionType.SINGLE)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (poll.status() != PollStatus.CLOSED) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_NOT_CLOSED);
		}
		if (settlementRepository.existsByPollId(poll.id())) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED);
		}
		PaymentAccount account = findOwnedActiveMealAccount(command);
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		Map<Long, PollOption> optionsById = new LinkedHashMap<>();
		options.forEach(option -> optionsById.put(option.id(), option));
		List<PollResponse> responses = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id());
		Map<Long, PollResponse> responsesById = new HashMap<>();
		responses.forEach(response -> responsesById.put(response.id(), response));
		Map<Long, List<PollResponse>> responsesByOption = responsesByOption(responses, responsesById, optionsById);
		Map<Long, CreateMealPollChargeGroupCommand> commandsByOption = validateGroups(command.groups(), responsesByOption.keySet());
		List<CalculatedGroup> calculatedGroups = calculateGroups(optionsById, responsesByOption, commandsByOption);
		long requestedTotal = 0;
		long actualTotal = 0;
		long roundingTotal = 0;
		try {
			for (CalculatedGroup group : calculatedGroups) {
				requestedTotal = Math.addExact(requestedTotal, group.requestedTotalAmount());
				actualTotal = Math.addExact(actualTotal, group.actualTotalAmount());
				roundingTotal = Math.addExact(roundingTotal, group.roundingAdjustment());
			}
		} catch (ArithmeticException exception) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_AMOUNT_OVERFLOW);
		}
		Instant chargedAt = Instant.now();
		MealPollSettlement settlement;
		try {
			settlement = settlementRepository.saveAndFlush(MealPollSettlement.create(
				command.campusId(), poll.id(), account.id(), command.requesterId(),
				requestedTotal, actualTotal, roundingTotal, chargedAt
			));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED);
		}
		chargeGroupRepository.saveAll(calculatedGroups.stream()
			.map(group -> MealPollChargeGroup.create(
				settlement.id(), poll.id(), group.option().id(), group.calculationType(), group.enteredAmount(),
				group.responses().size(), group.amountPerMember(), group.requestedTotalAmount(),
				group.actualTotalAmount(), group.roundingAdjustment()
			))
			.toList());
		for (CalculatedGroup group : calculatedGroups) {
			for (PollResponse response : group.responses()) {
				chargeCreationService.createMealCharge(new CreateMealChargeCommand(
					command.campusId(), response.userId(), command.requesterId(), account.id(), response.id(),
					group.option().content(), group.amountPerMember()
				));
			}
		}
		try {
			chargeItemRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED);
		}
		return new MealPollSettlementResult(
			poll.id(), account.id(), responses.size(), requestedTotal, actualTotal, roundingTotal, chargedAt,
			calculatedGroups.stream().map(CalculatedGroup::toResult).toList()
		);
	}

	private PaymentAccount findOwnedActiveMealAccount(CreateMealPollChargesCommand command) {
		return paymentAccountRepository.findById(command.paymentAccountId())
			.filter(account -> !account.isDeleted())
			.filter(PaymentAccount::isActive)
			.filter(account -> account.campusId().equals(command.campusId()))
			.filter(account -> account.accountType() == PaymentCategory.MEAL)
			.filter(account -> account.ownerUserId().equals(command.requesterId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND));
	}

	private Map<Long, List<PollResponse>> responsesByOption(
		List<PollResponse> responses,
		Map<Long, PollResponse> responsesById,
		Map<Long, PollOption> optionsById
	) {
		Map<Long, List<PollResponse>> result = new LinkedHashMap<>();
		List<PollResponseOption> selections = responses.isEmpty()
			? List.of()
			: pollResponseOptionRepository.findByResponseIdIn(responsesById.keySet());
		Set<Long> selectedResponseIds = new HashSet<>();
		for (PollResponseOption selection : selections) {
			PollResponse response = responsesById.get(selection.responseId());
			if (response == null || !optionsById.containsKey(selection.optionId())
				|| !selectedResponseIds.add(response.id())) {
				throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS);
			}
			result.computeIfAbsent(selection.optionId(), ignored -> new ArrayList<>()).add(response);
		}
		if (selectedResponseIds.size() != responses.size()) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS);
		}
		return result;
	}

	private Map<Long, CreateMealPollChargeGroupCommand> validateGroups(
		List<CreateMealPollChargeGroupCommand> commands,
		Set<Long> expectedOptionIds
	) {
		Map<Long, CreateMealPollChargeGroupCommand> byOption = new LinkedHashMap<>();
		for (CreateMealPollChargeGroupCommand command : commands) {
			if (command == null || command.optionId() == null || command.calculationType() == null
				|| command.enteredAmount() <= 0 || byOption.putIfAbsent(command.optionId(), command) != null) {
				throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS);
			}
		}
		if (!byOption.keySet().equals(expectedOptionIds)) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS);
		}
		return byOption;
	}

	private List<CalculatedGroup> calculateGroups(
		Map<Long, PollOption> optionsById,
		Map<Long, List<PollResponse>> responsesByOption,
		Map<Long, CreateMealPollChargeGroupCommand> commandsByOption
	) {
		List<CalculatedGroup> groups = new ArrayList<>();
		for (Map.Entry<Long, List<PollResponse>> entry : responsesByOption.entrySet()) {
			CreateMealPollChargeGroupCommand command = commandsByOption.get(entry.getKey());
			MealChargeCalculation calculation = mealChargeCalculator.calculate(
				command.calculationType(), command.enteredAmount(), entry.getValue().size()
			);
			groups.add(new CalculatedGroup(
				optionsById.get(entry.getKey()), command.calculationType(), command.enteredAmount(),
				entry.getValue(), calculation.amountPerMember(), calculation.requestedTotalAmount(),
				calculation.actualTotalAmount(), calculation.roundingAdjustment()
			));
		}
		return groups;
	}

	private record CalculatedGroup(
		PollOption option,
		MealChargeCalculationType calculationType,
		long enteredAmount,
		List<PollResponse> responses,
		int amountPerMember,
		long requestedTotalAmount,
		long actualTotalAmount,
		long roundingAdjustment
	) {
		MealPollChargeGroupResult toResult() {
			return new MealPollChargeGroupResult(
				option.id(), option.content(), calculationType, enteredAmount, responses.size(), amountPerMember,
				requestedTotalAmount, actualTotalAmount, roundingAdjustment
			);
		}
	}
}
