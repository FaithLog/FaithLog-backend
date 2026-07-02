package com.faithlog.billing.application;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.application.policy.BillingAccessPolicy;
import com.faithlog.billing.application.policy.ChargeStatusPolicy;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

	private static final String ACCOUNT_LIST_FORBIDDEN = "캠퍼스 납부 계좌 조회 권한이 없습니다.";

	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public BillingService(
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	@Transactional
	public PaymentAccountResult createPaymentAccount(CreatePaymentAccountCommand command) {
		requirePaymentAccountManager(command.campusId(), command.requesterId(), command.accountType());
		lockCampusOrThrow(command.campusId());
		Long ownerUserId = resolveOwnerUserId(command);

		deactivatePreviousActiveAccount(command.campusId(), command.accountType(), ownerUserId);

		PaymentAccount account = paymentAccountRepository.save(PaymentAccount.create(
			command.campusId(),
			command.accountType(),
			command.nickname(),
			command.bankName(),
			command.accountNumber(),
			command.accountHolder(),
			ownerUserId
		));

		if (account.accountType() == PaymentCategory.PENALTY) {
			reconnectUnpaidCharges(account);
		}
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public PaymentAccountResult deactivatePaymentAccount(Long accountId, Long requesterId) {
		PaymentAccount account = paymentAccountRepository.findById(accountId)
			.filter(paymentAccount -> !paymentAccount.isDeleted())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
		requireCoffeeAccountOwnerIfNeeded(account, requesterId);
		requirePaymentAccountManager(account.campusId(), requesterId, account.accountType());

		account.deactivate();
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public PaymentAccountResult activatePenaltyPaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		lockCampusOrThrow(campusId);
		PaymentAccount account = findPaymentAccountInCampus(campusId, paymentAccountId);
		if (account.accountType() != PaymentCategory.PENALTY) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED);
		}
		requirePaymentAccountManager(campusId, requesterId, PaymentCategory.PENALTY);
		if (account.isActive()) {
			return PaymentAccountResult.from(account);
		}

		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY)
			.filter(activeAccount -> !activeAccount.id().equals(account.id()))
			.ifPresent(PaymentAccount::deactivate);
		account.activate();
		reconnectUnpaidCharges(account);
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public void deletePaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		PaymentAccount account = findPaymentAccountInCampus(campusId, paymentAccountId);
		requireCoffeeAccountOwnerIfNeeded(account, requesterId);
		requirePaymentAccountManager(campusId, requesterId, account.accountType());
		if (account.isActive()) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN);
		}
		account.softDelete();
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listPaymentAccounts(Long campusId, Long requesterId) {
		requirePaymentAccountListAccess(campusId, requesterId);
		return paymentAccountRepository.findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId)
			.stream()
			.map(PaymentAccountResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listAdminPaymentAccounts(Long campusId, Long requesterId) {
		return listAdminPaymentAccounts(campusId, requesterId, null, false);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listAdminPaymentAccounts(
		Long campusId,
		Long requesterId,
		PaymentCategory accountType,
		boolean includeInactive
	) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin() || isCampusManager(campusId, requester.userId())) {
			return findAdminPaymentAccounts(campusId, accountType, includeInactive);
		}
		if (isActiveCoffeeDuty(campusId, requester.userId()) && (accountType == null || accountType == PaymentCategory.COFFEE)) {
			return paymentAccountRepository
				.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
					campusId,
					requester.userId(),
					PaymentCategory.COFFEE
				)
				.stream()
				.map(PaymentAccountResult::from)
				.toList();
		}
		throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN, ACCOUNT_LIST_FORBIDDEN);
	}

	@Transactional(readOnly = true)
	public void requireActivePenaltyAccount(Long campusId) {
		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
	}

	@Transactional
	public ChargeItemResult createPenaltyCharge(CreatePenaltyChargeCommand command) {
		PaymentAccount account = paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(command.campusId(), PaymentCategory.PENALTY)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));

		ChargeItem existingCharge = chargeItemRepository
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
				command.campusId(),
				command.userId(),
				PaymentCategory.PENALTY,
				command.sourceType(),
				command.sourceId()
			)
			.orElse(null);
		if (existingCharge != null && existingCharge.isUnpaid()) {
			existingCharge.updateUnpaidCharge(
				account,
				command.title(),
				command.reason(),
				command.amount(),
				command.dueDate()
			);
			return ChargeItemResult.from(existingCharge);
		}
		if (existingCharge != null) {
			throw new BusinessException(ErrorCode.BILLING_TERMINAL_CHARGE_UPDATE_FORBIDDEN);
		}

		ChargeItem chargeItem = ChargeItem.create(
			command.campusId(),
			command.userId(),
			PaymentCategory.PENALTY,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			command.sourceType(),
			command.sourceId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		);
		return ChargeItemResult.from(chargeItemRepository.save(chargeItem));
	}

	@Transactional
	public ChargeItemResult createOrUpdateCoffeeCharge(CreateCoffeeChargeCommand command) {
		PaymentAccount account = findValidCoffeeAccount(command);
		ChargeItem existingCharge = chargeItemRepository
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
				command.campusId(),
				command.userId(),
				PaymentCategory.COFFEE,
				ChargeSourceType.POLL_RESPONSE,
				command.sourceId()
			)
			.orElse(null);
		if (existingCharge != null && existingCharge.isUnpaid()) {
			existingCharge.updateUnpaidCharge(
				account,
				command.title(),
				command.reason(),
				command.amount(),
				command.dueDate()
			);
			return ChargeItemResult.from(existingCharge);
		}
		if (existingCharge != null) {
			return ChargeItemResult.from(existingCharge);
		}

		ChargeItem chargeItem = ChargeItem.create(
			command.campusId(),
			command.userId(),
			PaymentCategory.COFFEE,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			ChargeSourceType.POLL_RESPONSE,
			command.sourceId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		);
		return ChargeItemResult.from(chargeItemRepository.save(chargeItem));
	}

	@Transactional
	public ChargeItemResult completeMyChargePayment(CompleteChargePaymentCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		ChargeItem chargeItem = chargeItemRepository.findChargeItemById(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));

		if (!chargeItem.userId().equals(requester.userId())) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_PAYMENT_FORBIDDEN);
		}
		if (!chargeItem.campusId().equals(command.campusId())) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_CAMPUS_MISMATCH);
		}
		requireActiveCampusMember(command.campusId(), requester.userId(), ErrorCode.BILLING_MY_CHARGE_PAYMENT_FORBIDDEN);
		if (!chargeItem.isUnpaid()) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_PAYMENT_CONFLICT);
		}

		chargeItem.markPaid(command.paidAt());
		return ChargeItemResult.from(chargeItem);
	}

	@Transactional
	public ChargeItemResult changeChargeStatus(ChangeChargeStatusCommand command) {
		ChargeItem chargeItem = chargeItemRepository.findChargeItemById(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));
		requireChargeStatusManager(chargeItem.campusId(), command.requesterId());
		ChargeStatusPolicy.applyAdminStatusChange(chargeItem, command.status());

		return ChargeItemResult.from(chargeItem);
	}

	private void reconnectUnpaidCharges(PaymentAccount account) {
		chargeItemRepository.findByCampusIdAndPaymentCategoryAndStatus(
				account.campusId(),
				account.accountType(),
				ChargeStatus.UNPAID
			)
			.forEach(chargeItem -> chargeItem.reconnectPaymentAccount(account));
	}

	private Long resolveOwnerUserId(CreatePaymentAccountCommand command) {
		if (command.accountType() == PaymentCategory.PENALTY) {
			return command.ownerUserId() == null ? command.requesterId() : command.ownerUserId();
		}
		if (command.ownerUserId() != null && !command.ownerUserId().equals(command.requesterId())) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN);
		}
		return command.requesterId();
	}

	private void deactivatePreviousActiveAccount(Long campusId, PaymentCategory accountType, Long ownerUserId) {
		if (accountType == PaymentCategory.COFFEE) {
			paymentAccountRepository
				.findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNull(campusId, accountType, ownerUserId)
				.ifPresent(PaymentAccount::deactivate);
			return;
		}
		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, accountType)
			.ifPresent(PaymentAccount::deactivate);
	}

	private void requireCoffeeAccountOwnerIfNeeded(PaymentAccount account, Long requesterId) {
		if (account.accountType() != PaymentCategory.COFFEE) {
			return;
		}
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		if (!requester.userId().equals(account.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN);
		}
	}

	private PaymentAccount findValidCoffeeAccount(CreateCoffeeChargeCommand command) {
		if (command.paymentAccountId() == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(command.paymentAccountId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (account.isDeleted() || !account.isActive() || !account.campusId().equals(command.campusId()) || account.accountType() != PaymentCategory.COFFEE) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		return account;
	}

	private List<PaymentAccountResult> findAdminPaymentAccounts(
		Long campusId,
		PaymentCategory accountType,
		boolean includeInactive
	) {
		List<PaymentAccount> accounts;
		if (accountType != null && includeInactive) {
			accounts = paymentAccountRepository.findByCampusIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(campusId, accountType);
		} else if (accountType != null) {
			accounts = paymentAccountRepository
				.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId, accountType);
		} else if (includeInactive) {
			accounts = paymentAccountRepository.findByCampusIdAndDeletedAtIsNullOrderByIdAsc(campusId);
		} else {
			accounts = paymentAccountRepository.findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId);
		}
		return accounts.stream()
			.map(PaymentAccountResult::from)
			.toList();
	}

	private PaymentAccount findPaymentAccountInCampus(Long campusId, Long paymentAccountId) {
		return paymentAccountRepository.findById(paymentAccountId)
			.filter(account -> !account.isDeleted())
			.filter(account -> account.campusId().equals(campusId))
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
	}

	private void requirePaymentAccountManager(Long campusId, Long requesterId, PaymentCategory accountType) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
		if (accountType == PaymentCategory.COFFEE && isActiveCoffeeDuty(campusId, requester.userId())) {
			return;
		}
		BillingAccessPolicy.requirePaymentAccountManager(requesterMembership);
	}

	private boolean isCampusManager(Long campusId, Long requesterId) {
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requesterId)
			.filter(CampusMember::isActive)
			.map(membership -> {
				try {
					BillingAccessPolicy.requirePaymentAccountManager(membership);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			})
			.orElse(false);
	}

	private boolean isActiveCoffeeDuty(Long campusId, Long userId) {
		return dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
			.map(assignment -> assignment.userId().equals(userId))
			.orElse(false);
	}

	private void requirePaymentAccountListAccess(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		requireActiveCampusMember(campusId, requester.userId(), ErrorCode.BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN, ACCOUNT_LIST_FORBIDDEN);
	}

	private void requireChargeStatusManager(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN));
		BillingAccessPolicy.requireChargeStatusManager(requesterMembership);
	}

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode) {
		requireActiveCampusMember(campusId, userId, errorCode, errorCode.message());
	}

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode, String message) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode, message));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
