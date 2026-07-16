package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.ChargeCreationService;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
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
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.command.CreateMealPollChargeGroupCommand;
import com.faithlog.poll.service.command.CreateMealPollChargesCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MealPollSettlementBatchFailureUnitTest {

	@Test
	void maps_batch_save_unique_conflict_to_existing_meal_duplicate_error() {
		MealDutyAccessService dutyAccess = mock(MealDutyAccessService.class);
		PollLookupSupport pollLookup = mock(PollLookupSupport.class);
		PollOptionRepository optionRepository = mock(PollOptionRepository.class);
		PollResponseRepository responseRepository = mock(PollResponseRepository.class);
		PollResponseOptionRepository responseOptionRepository = mock(PollResponseOptionRepository.class);
		PaymentAccountRepositoryPort accountRepository = mock(PaymentAccountRepositoryPort.class);
		MealPollSettlementRepository settlementRepository = mock(MealPollSettlementRepository.class);
		MealPollChargeGroupRepository groupRepository = mock(MealPollChargeGroupRepository.class);
		ChargeCreationService chargeCreationService = mock(ChargeCreationService.class);
		ChargeItemRepositoryPort chargeItemRepository = mock(ChargeItemRepositoryPort.class);
		MealChargeCalculator calculator = mock(MealChargeCalculator.class);

		Poll poll = mock(Poll.class);
		when(poll.id()).thenReturn(2L);
		when(poll.pollType()).thenReturn(PollType.MEAL);
		when(poll.selectionType()).thenReturn(SelectionType.SINGLE);
		when(poll.status()).thenReturn(PollStatus.CLOSED);
		when(pollLookup.getPollInCampusForUpdate(1L, 2L)).thenReturn(poll);

		PaymentAccount account = mock(PaymentAccount.class);
		when(account.id()).thenReturn(20L);
		when(account.campusId()).thenReturn(1L);
		when(account.accountType()).thenReturn(PaymentCategory.MEAL);
		when(account.ownerUserId()).thenReturn(7L);
		when(account.isActive()).thenReturn(true);
		when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

		PollOption option = mock(PollOption.class);
		when(option.id()).thenReturn(10L);
		when(option.content()).thenReturn("lunch");
		when(optionRepository.findByPollIdOrderBySortOrderAsc(2L)).thenReturn(List.of(option));

		PollResponse response = mock(PollResponse.class);
		when(response.id()).thenReturn(30L);
		when(response.userId()).thenReturn(40L);
		when(responseRepository.findByPollIdOrderByIdAsc(2L)).thenReturn(List.of(response));
		when(responseOptionRepository.findByResponseIdInOrderByIdAsc(Set.of(30L)))
			.thenReturn(List.of(PollResponseOption.create(30L, 10L)));
		when(calculator.calculate(MealChargeCalculationType.PER_MEMBER, 5_000L, 1))
			.thenReturn(new MealChargeCalculation(5_000, 5_000L, 5_000L, 0L));

		MealPollSettlement settlement = mock(MealPollSettlement.class);
		when(settlement.id()).thenReturn(50L);
		when(settlementRepository.saveAndFlush(any(MealPollSettlement.class))).thenReturn(settlement);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(chargeCreationService).createMealCharges(anyList());

		MealPollSettlementService service = new MealPollSettlementService(
			dutyAccess,
			pollLookup,
			optionRepository,
			responseRepository,
			responseOptionRepository,
			accountRepository,
			settlementRepository,
			groupRepository,
			chargeCreationService,
			chargeItemRepository,
			calculator,
			Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)
		);
		CreateMealPollChargesCommand command = new CreateMealPollChargesCommand(
			1L,
			2L,
			7L,
			20L,
			List.of(new CreateMealPollChargeGroupCommand(10L, MealChargeCalculationType.PER_MEMBER, 5_000L))
		);

		assertThatThrownBy(() -> service.settle(command))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED));
	}
}
