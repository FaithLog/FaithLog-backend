package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class MealDutyAccessService {

	private final CampusUserLookupPort userLookupPort;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public MealDutyAccessService(
		CampusUserLookupPort userLookupPort,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.userLookupPort = userLookupPort;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	public CampusUserLookupResult requireActiveMealDuty(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = userLookupPort.findCampusUserById(requesterId)
			.filter(CampusUserLookupResult::active)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEAL_DUTY_REQUIRED));
		dutyAssignmentRepository.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
			campusId,
			DutyType.MEAL,
			requester.userId()
		).orElseThrow(() -> new BusinessException(ErrorCode.MEAL_DUTY_REQUIRED));
		return requester;
	}
}
