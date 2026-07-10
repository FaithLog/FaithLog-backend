package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusDutyAssignmentService {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusDutyAssignmentService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional(readOnly = true)
	public List<DutyAssignmentResult> getDutyAssignments(Long campusId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		return dutyAssignmentRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(assignment -> DutyAssignmentResult.of(
				assignment,
				campusAccessPolicy.getUserOrThrow(assignment.userId())
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public MyDutyAssignmentResult getMyCoffeeDutyAssignment(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN));
		boolean active = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
			.map(assignment -> assignment.userId().equals(requester.userId()))
			.orElse(false);
		return new MyDutyAssignmentResult(requester.userId(), campusId, DutyType.COFFEE.name(), active);
	}

	@Transactional
	public DutyAssignmentResult assignCoffeeDuty(AssignCoffeeDutyCommand command) {
		campusAccessPolicy.requireCampusManager(
			command.campusId(),
			command.requesterId(),
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember targetMember = campusMemberRepository
			.findByCampusIdAndUserId(command.campusId(), command.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CAMPUS_MEMBER_NOT_FOUND,
				"커피 담당자로 지정할 캠퍼스 멤버를 찾을 수 없습니다."
			));

		dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(command.campusId(), DutyType.COFFEE)
			.ifPresent(CampusDutyAssignment::revoke);
		CampusDutyAssignment assignment = dutyAssignmentRepository.save(
			CampusDutyAssignment.assignCoffee(command.campusId(), targetMember.userId())
		);
		return DutyAssignmentResult.of(
			assignment,
			campusAccessPolicy.getUserOrThrow(assignment.userId())
		);
	}

	@Transactional
	public void revokeCoffeeDuty(Long campusId, Long assignmentId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndId(campusId, DutyType.COFFEE, assignmentId)
			.filter(CampusDutyAssignment::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND));
		assignment.revoke();
	}
}
