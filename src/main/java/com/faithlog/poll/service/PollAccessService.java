package com.faithlog.poll.service;

import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class PollAccessService {

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	PollAccessService(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	void requireTemplateManager(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN);
	}

	void requireCoffeeTemplateManager(Long campusId, Long requesterId) {
		requireActiveCoffeeDuty(campusId, requesterId, ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN);
	}

	void requireCoffeeTemplateManagerForUpdate(Long campusId, Long requesterId) {
		requireActiveCoffeeDutyForUpdate(campusId, requesterId, ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN);
	}

	void requirePollCreator(Long campusId, Long requesterId, PollType pollType) {
		if (pollType == PollType.COFFEE) {
			requireActiveCoffeeDuty(campusId, requesterId, ErrorCode.POLL_CREATE_FORBIDDEN);
			return;
		}
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_CREATE_FORBIDDEN);
	}

	void requirePollCreatorForUpdate(
		Long campusId,
		Long requesterId,
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		if (CoffeeOperationClassifier.isCoffeeOperation(pollType, chargeGenerationType, paymentCategory)) {
			requireActiveCoffeeDutyForUpdate(campusId, requesterId, ErrorCode.POLL_CREATE_FORBIDDEN);
			return;
		}
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_CREATE_FORBIDDEN);
	}

	CampusMember requireActiveCampusMember(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_ACCESS_FORBIDDEN));
	}

	void requirePollReader(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_ACCESS_FORBIDDEN));
	}

	void requirePollAdmin(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_ADMIN_FORBIDDEN);
	}

	void requirePollAdmin(Long campusId, Long requesterId, PollType pollType) {
		requireCampusManagerOrCoffeeDuty(campusId, requesterId, pollType, ErrorCode.POLL_ADMIN_FORBIDDEN);
	}

	void requireCoffeePollOwner(Long campusId, Long requesterId, Poll poll) {
		requireActiveCoffeeDuty(campusId, requesterId, ErrorCode.POLL_ADMIN_FORBIDDEN);
		if (!requesterId.equals(poll.createdBy())) {
			throw new BusinessException(ErrorCode.POLL_ADMIN_FORBIDDEN);
		}
	}

	void requireCoffeePollOwnerForUpdate(Long campusId, Long requesterId, Poll poll) {
		requireActiveCoffeeDutyForUpdate(campusId, requesterId, ErrorCode.POLL_ADMIN_FORBIDDEN);
		if (!requesterId.equals(poll.createdBy())) {
			throw new BusinessException(ErrorCode.POLL_ADMIN_FORBIDDEN);
		}
	}

	void requireCoffeePollOwnerForUpdate(
		Long campusId,
		Long requesterId,
		PollRepository.PollLockScope poll
	) {
		requireActiveCoffeeDutyForUpdate(campusId, requesterId, ErrorCode.POLL_ADMIN_FORBIDDEN);
		if (!requesterId.equals(poll.getCreatedBy())) {
			throw new BusinessException(ErrorCode.POLL_ADMIN_FORBIDDEN);
		}
	}

	boolean hasAdminVisibility(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return true;
		}
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.map(member -> {
				try {
					CampusRolePolicy.requireCampusManager(member, ErrorCode.POLL_ADMIN_FORBIDDEN);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			})
			.orElse(false);
	}

	CampusUserLookupResult getUser(Long userId) {
		return getActiveUser(userId);
	}

	Map<Long, CampusUserLookupResult> getUsers(Collection<Long> userIds) {
		Set<Long> distinctUserIds = new HashSet<>(userIds);
		Map<Long, CampusUserLookupResult> usersById = new HashMap<>();
		userLookupPort.findCampusUsersByIds(distinctUserIds)
			.forEach(user -> usersById.put(user.userId(), user));
		for (Long userId : distinctUserIds) {
			CampusUserLookupResult user = usersById.get(userId);
			if (user == null || !user.active()) {
				throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
			}
		}
		return usersById;
	}

	private void requireCampusManager(Long campusId, Long requesterId, ErrorCode errorCode) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode);
	}

	private void requireCampusManagerOrCoffeeDuty(Long campusId, Long requesterId, PollType pollType, ErrorCode errorCode) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (pollType == PollType.COFFEE && isActiveCoffeeDuty(campusId, requester.userId())) {
			return;
		}
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode);
	}

	private void requireActiveCoffeeDuty(Long campusId, Long requesterId, ErrorCode errorCode) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (!isActiveCoffeeDuty(campusId, requester.userId())) {
			throw new BusinessException(errorCode);
		}
	}

	private void requireActiveCoffeeDutyForUpdate(Long campusId, Long requesterId, ErrorCode errorCode) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.COFFEE, requester.userId()).isEmpty()) {
			throw new BusinessException(errorCode);
		}
	}

	boolean isActiveCoffeeDuty(Long campusId, Long userId) {
		return dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(campusId, DutyType.COFFEE, userId)
			.isPresent();
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}
}
