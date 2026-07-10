package com.faithlog.prayer.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PrayerAccessSupport {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	PrayerAccessSupport(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	CampusUserLookupResult requirePrayerReader(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId, ErrorCode.AUTH_UNAUTHORIZED);
		if (requester.isAdmin()) {
			return requester;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_ACCESS_FORBIDDEN));
		return requester;
	}

	void requirePrayerManager(Long campusId, Long requesterId) {
		if (campusRepository.findById(campusId).filter(campus -> campus.isActive()).isEmpty()) {
			throw new BusinessException(ErrorCode.CAMPUS_NOT_FOUND);
		}
		CampusUserLookupResult requester = getActiveUser(requesterId, ErrorCode.AUTH_UNAUTHORIZED);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_MANAGE_FORBIDDEN));
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(ErrorCode.PRAYER_MANAGE_FORBIDDEN);
		}
	}

	boolean isCampusManager(Long campusId, Long userId) {
		return campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.map(CampusMember::canManageCampusMembers)
			.orElse(false);
	}

	Map<Long, CampusMember> activeCampusMemberMap(Long campusId) {
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.collect(Collectors.toMap(CampusMember::userId, Function.identity()));
	}

	CampusUserLookupResult getActiveUser(Long userId, ErrorCode errorCode) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (!user.active()) {
			throw new BusinessException(errorCode);
		}
		return user;
	}

	CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND));
	}

	Map<Long, CampusUserLookupResult> campusUsersById(Collection<Long> userIds) {
		Map<Long, CampusUserLookupResult> usersById = userLookupPort.findCampusUsersByIds(userIds)
			.stream()
			.collect(Collectors.toMap(CampusUserLookupResult::userId, Function.identity()));
		for (Long userId : userIds) {
			if (!usersById.containsKey(userId)) {
				throw new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND);
			}
		}
		return usersById;
	}
}
