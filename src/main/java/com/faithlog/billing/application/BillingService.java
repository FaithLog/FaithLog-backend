package com.faithlog.billing.application;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

	private static final String ACCOUNT_MANAGE_FORBIDDEN = "납부 계좌 관리 권한이 없습니다.";
	private static final String ACCOUNT_LIST_FORBIDDEN = "캠퍼스 납부 계좌 조회 권한이 없습니다.";
	private static final String CONTACT_ADMIN = "관리자에게 문의하세요";

	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public BillingService(
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional
	public PaymentAccountResult createPaymentAccount(CreatePaymentAccountCommand command) {
		requireCampusManager(command.campusId(), command.requesterId());
		lockCampusOrThrow(command.campusId());

		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrue(command.campusId(), command.accountType())
			.ifPresent(PaymentAccount::deactivate);

		PaymentAccount account = paymentAccountRepository.save(PaymentAccount.create(
			command.campusId(),
			command.accountType(),
			command.nickname(),
			command.bankName(),
			command.accountNumber(),
			command.accountHolder(),
			command.ownerUserId()
		));

		reconnectUnpaidCharges(account);
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public PaymentAccountResult deactivatePaymentAccount(Long accountId, Long requesterId) {
		PaymentAccount account = paymentAccountRepository.findById(accountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "납부 계좌를 찾을 수 없습니다."));
		requireCampusManager(account.campusId(), requesterId);

		account.deactivate();
		return PaymentAccountResult.from(account);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listPaymentAccounts(Long campusId, Long requesterId) {
		requireActiveCampusMember(campusId, requesterId);
		return paymentAccountRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(PaymentAccountResult::from)
			.toList();
	}

	@Transactional
	public ChargeItemResult createPenaltyCharge(CreatePenaltyChargeCommand command) {
		PaymentAccount account = paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrue(command.campusId(), PaymentCategory.PENALTY)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, CONTACT_ADMIN));

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

	private void reconnectUnpaidCharges(PaymentAccount account) {
		chargeItemRepository.findByCampusIdAndPaymentCategoryAndStatus(
				account.campusId(),
				account.accountType(),
				ChargeStatus.UNPAID
			)
			.forEach(chargeItem -> chargeItem.reconnectPaymentAccount(account));
	}

	private void requireCampusManager(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, ACCOUNT_MANAGE_FORBIDDEN));
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, ACCOUNT_MANAGE_FORBIDDEN);
		}
	}

	private void requireActiveCampusMember(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, ACCOUNT_LIST_FORBIDDEN));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return user;
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "캠퍼스를 찾을 수 없습니다."));
	}
}
