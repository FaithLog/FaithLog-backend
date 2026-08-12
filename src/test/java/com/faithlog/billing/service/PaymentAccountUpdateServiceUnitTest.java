package com.faithlog.billing.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
			new PaymentAccountLockScope(accountId, campusId, PaymentCategory.COFFEE, requesterId)
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
}
