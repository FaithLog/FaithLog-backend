package com.faithlog.campus.service.policy;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class CampusAccessPolicy {

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public CampusAccessPolicy(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	public CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	public CampusUserLookupResult getActiveUserForUpdate(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserByIdForUpdate(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	public CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
	}

	public Map<Long, CampusUserLookupResult> getUsers(Collection<Long> userIds) {
		Set<Long> orderedIds = new TreeSet<>(userIds);
		Map<Long, CampusUserLookupResult> users = new LinkedHashMap<>();
		userLookupPort.findCampusUsersByIds(orderedIds)
			.forEach(user -> users.put(user.userId(), user));
		ensureAllUsersFound(orderedIds, users);
		return users;
	}

	public Map<Long, CampusUserLookupResult> getUsersForUpdate(Collection<Long> userIds) {
		Set<Long> orderedIds = new TreeSet<>(userIds);
		Map<Long, CampusUserLookupResult> users = new LinkedHashMap<>();
		userLookupPort.findCampusUsersByIdsForUpdate(orderedIds)
			.forEach(user -> users.put(user.userId(), user));
		ensureAllUsersFound(orderedIds, users);
		return users;
	}

	public void requireCampusManager(Long campusId, Long requesterId, ErrorCode errorCode, String message) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode, message));
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode, message);
	}

	private void ensureAllUsersFound(
		Set<Long> userIds,
		Map<Long, CampusUserLookupResult> users
	) {
		if (!users.keySet().containsAll(userIds)) {
			throw new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND);
		}
	}
}
