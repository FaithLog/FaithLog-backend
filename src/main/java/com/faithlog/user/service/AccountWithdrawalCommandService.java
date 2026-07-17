package com.faithlog.user.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberLockScope;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.DeleteMyAccountCommand;
import com.faithlog.user.service.port.UserFcmTokenDeactivationPort;
import com.faithlog.user.service.result.DeleteMyAccountResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalCommandService {

	private static final String DELETE_CONFIRM_TEXT = "회원탈퇴";

	private final UserRepository userRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final AccountSoftDeletionSupport softDeletionSupport;
	private final UserSessionRevocationSupport sessionRevocationSupport;
	private final UserFcmTokenDeactivationPort fcmTokenDeactivationPort;

	public AccountWithdrawalCommandService(
		UserRepository userRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		AccountSoftDeletionSupport softDeletionSupport,
		UserSessionRevocationSupport sessionRevocationSupport,
		UserFcmTokenDeactivationPort fcmTokenDeactivationPort
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.softDeletionSupport = softDeletionSupport;
		this.sessionRevocationSupport = sessionRevocationSupport;
		this.fcmTokenDeactivationPort = fcmTokenDeactivationPort;
	}

	@Transactional
	public DeleteMyAccountResult deleteMyAccount(DeleteMyAccountCommand command) {
		if (command.userId() == null || command.sessionId() == null || command.accessJti() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		User user = userRepository.findByIdForUpdate(command.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive() || user.deletedAt() != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
		}
		if (!softDeletionSupport.passwordMatches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.USER_DELETE_PASSWORD_MISMATCH);
		}
		if (!DELETE_CONFIRM_TEXT.equals(command.confirmText())) {
			throw new BusinessException(ErrorCode.USER_DELETE_CONFIRM_TEXT_INVALID);
		}

		List<CampusMember> lockedMemberships = new ArrayList<>();
		boolean hasActiveDuty = false;
		for (CampusMemberLockScope membershipScope : campusMemberRepository
			.findLockScopesByUserIdOrderByCampusIdAsc(command.userId())) {
			campusRepository.findByIdForUpdate(membershipScope.campusId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
			if (!dutyAssignmentRepository.findActiveByCampusIdAndUserIdForUpdate(
				membershipScope.campusId(), command.userId()).isEmpty()) {
				hasActiveDuty = true;
			}
			lockedMemberships.add(campusMemberRepository.findByCampusIdAndIdForUpdate(
				membershipScope.campusId(), membershipScope.membershipId()
			).orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND)));
		}
		if (hasActiveDuty) {
			throw new BusinessException(ErrorCode.CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT);
		}

		Instant deletedAt = Instant.now();
		softDeletionSupport.deactivateCampusMemberships(lockedMemberships);
		fcmTokenDeactivationPort.deactivateAllForUser(user.id());
		sessionRevocationSupport.revokeAllSessions(user.id(), command.accessJti(), command.accessTokenExpiresAt());
		softDeletionSupport.softDelete(user, deletedAt);

		return new DeleteMyAccountResult(deletedAt);
	}
}
