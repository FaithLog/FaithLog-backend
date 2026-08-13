package com.faithlog.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.UpdatePaymentAccountCommand;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountLockScope;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentAccountUpdateServiceUnitTest {

	@Mock
	private PaymentAccountRepositoryPort paymentAccountRepository;

	@Mock
	private ChargeItemRepositoryPort chargeItemRepository;

	@Mock
	private CampusMemberRepositoryPort campusMemberRepository;

	@Mock
	private CampusUserLookupPort userLookupPort;

	@Mock
	private CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	@Mock
	private MealDutyAccessService mealDutyAccessService;

	@InjectMocks
	private PaymentAccountUpdateService paymentAccountUpdateService;

	@Test
	void coffee_update_locks_duty_before_account_to_match_deactivate_lock_order() {
		Long campusId = 257L;
		Long accountId = 1257L;
		Long requesterId = 2257L;
		PaymentAccount account = PaymentAccount.create(
			campusId,
			PaymentCategory.COFFEE,
			"커피 계좌",
			"기존은행",
			"257-OLD",
			"담당자",
			requesterId
		);
		ReflectionTestUtils.setField(account, "id", accountId);
		CampusMember membership = mock(CampusMember.class);
		when(membership.isActive()).thenReturn(true);
		when(paymentAccountRepository.findLockScopeById(accountId)).thenReturn(Optional.of(
			new PaymentAccountLockScope(accountId, campusId, PaymentCategory.COFFEE, requesterId, null)
		));
		when(userLookupPort.findCampusUserById(requesterId)).thenReturn(Optional.of(
			new CampusUserLookupResult(requesterId, "커피담당", "coffee@example.com", "USER", true)
		));
		when(campusMemberRepository.findByCampusIdAndUserId(campusId, requesterId))
			.thenReturn(Optional.of(membership));
		when(dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.COFFEE, requesterId
		)).thenReturn(Optional.of(mock(CampusDutyAssignment.class)));
		when(paymentAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
		when(chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAscForUpdate(
				campusId, PaymentCategory.COFFEE, ChargeStatus.UNPAID, Set.of(accountId)
			)).thenReturn(List.of());

		paymentAccountUpdateService.updatePaymentAccount(new UpdatePaymentAccountCommand(
			campusId, accountId, requesterId, "수정 계좌", "새은행", "257-NEW", "담당자"
		));

		InOrder lockOrder = inOrder(paymentAccountRepository, dutyAssignmentRepository);
		lockOrder.verify(paymentAccountRepository).findLockScopeById(accountId);
		lockOrder.verify(dutyAssignmentRepository)
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(campusId, DutyType.COFFEE, requesterId);
		lockOrder.verify(paymentAccountRepository).findByIdForUpdate(accountId);
	}

	@Test
	void meal_update_locks_duty_before_account_to_match_deactivate_lock_order() {
		Long campusId = 257L;
		Long accountId = 3257L;
		Long requesterId = 4257L;
		PaymentAccount account = PaymentAccount.create(
			campusId,
			PaymentCategory.MEAL,
			"밥 계좌",
			"기존은행",
			"257-MEAL-OLD",
			"담당자",
			requesterId
		);
		ReflectionTestUtils.setField(account, "id", accountId);
		when(paymentAccountRepository.findLockScopeById(accountId)).thenReturn(Optional.of(
			new PaymentAccountLockScope(accountId, campusId, PaymentCategory.MEAL, requesterId, null)
		));
		when(paymentAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
		when(chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAscForUpdate(
				campusId, PaymentCategory.MEAL, ChargeStatus.UNPAID, Set.of(accountId)
			)).thenReturn(List.of());

		paymentAccountUpdateService.updatePaymentAccount(new UpdatePaymentAccountCommand(
			campusId, accountId, requesterId, "수정 계좌", "새은행", "257-MEAL-NEW", "담당자"
		));

		InOrder lockOrder = inOrder(paymentAccountRepository, mealDutyAccessService);
		lockOrder.verify(paymentAccountRepository).findLockScopeById(accountId);
		lockOrder.verify(mealDutyAccessService).requireActiveMealDutyForUpdate(campusId, requesterId);
		lockOrder.verify(paymentAccountRepository).findByIdForUpdate(accountId);
	}

	@Test
	void deleted_account_is_hidden_before_authorization_or_write_locks() {
		Long accountId = 5257L;
		when(paymentAccountRepository.findLockScopeById(accountId)).thenReturn(Optional.of(
			new PaymentAccountLockScope(
				accountId,
				257L,
				PaymentCategory.COFFEE,
				6257L,
				Instant.parse("2026-08-12T00:00:00Z")
			)
		));

		assertThatThrownBy(() ->
			paymentAccountUpdateService.updatePaymentAccount(new UpdatePaymentAccountCommand(
				257L, accountId, 6257L, "수정 계좌", "새은행", "257-NEW", "담당자"
			))
		).isInstanceOfSatisfying(BusinessException.class, exception ->
			assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));

		verify(dutyAssignmentRepository, never())
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(257L, DutyType.COFFEE, 6257L);
		verify(paymentAccountRepository, never()).findByIdForUpdate(accountId);
	}
}
