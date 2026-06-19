package com.faithlog.campus.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusDutyAssignment;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.application.policy.CampusRolePolicy;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusService {

	private static final int INVITE_CODE_MAX_ATTEMPTS = 20;

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CampusUserLookupPort userLookupPort;
	private final InviteCodeGenerator inviteCodeGenerator;

	public CampusService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CampusUserLookupPort userLookupPort,
		InviteCodeGenerator inviteCodeGenerator
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.userLookupPort = userLookupPort;
		this.inviteCodeGenerator = inviteCodeGenerator;
	}

	@Transactional
	public CampusCreateResult createCampus(CreateCampusCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		CampusRolePolicy.requireCampusCreator(requester);

		Campus campus = campusRepository.save(Campus.create(
			command.name(),
			command.region(),
			command.description(),
			generateUniqueInviteCode()
		));
		CampusMember creatorMembership = campusMemberRepository.save(CampusMember.createMinister(campus.id(), requester.userId()));
		return CampusCreateResult.of(campus, creatorMembership);
	}

	@Transactional
	public CampusMembershipResult joinCampus(JoinCampusCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		Campus campus = campusRepository.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_INVALID_INVITE_CODE));

		CampusMember existingMember = campusMemberRepository.findByCampusIdAndUserId(campus.id(), requester.userId()).orElse(null);
		if (existingMember != null && existingMember.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_ALREADY_JOINED);
		}
		if (existingMember != null) {
			existingMember.reactivateAsMember();
			return CampusMembershipResult.of(campus, existingMember);
		}

		CampusMember member = campusMemberRepository.save(CampusMember.createMember(campus.id(), requester.userId()));
		return CampusMembershipResult.of(campus, member);
	}

	@Transactional
	public void deleteCampusMember(Long campusId, Long membershipId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndId(campusId, membershipId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(campusId, requester.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN));
			CampusRolePolicy.requireCampusManager(requesterMembership, ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
		}

		targetMember.deactivate();
	}

	@Transactional
	public AdminCampusMemberResult changeCampusRole(ChangeCampusRoleCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndId(command.campusId(), command.campusMemberId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(command.campusId(), requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_ROLE_CHANGE_FORBIDDEN));
			CampusRolePolicy.requireRoleChangeAllowed(
				requesterMembership,
				targetMember.campusRole(),
				command.campusRole()
			);
		}

		targetMember.changeCampusRole(command.campusRole());
		return AdminCampusMemberResult.of(targetMember, getUserOrThrow(targetMember.userId()));
	}

	@Transactional(readOnly = true)
	public List<AdminCampusMemberResult> getCampusMembers(Long campusId, Long requesterId) {
		requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"캠퍼스 멤버 관리 권한이 없습니다."
		);
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> AdminCampusMemberResult.of(member, getUserOrThrow(member.userId())))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<DutyAssignmentResult> getDutyAssignments(Long campusId, Long requesterId) {
		requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		return dutyAssignmentRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(assignment -> DutyAssignmentResult.of(assignment, getUserOrThrow(assignment.userId())))
			.toList();
	}

	@Transactional
	public DutyAssignmentResult assignCoffeeDuty(AssignCoffeeDutyCommand command) {
		requireCampusManager(
			command.campusId(),
			command.requesterId(),
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		lockCampusOrThrow(command.campusId());
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndUserId(command.campusId(), command.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CAMPUS_MEMBER_NOT_FOUND,
				"커피 담당자로 지정할 캠퍼스 멤버를 찾을 수 없습니다."
			));

		dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(command.campusId(), DutyType.COFFEE)
			.ifPresent(CampusDutyAssignment::revoke);
		CampusDutyAssignment assignment = dutyAssignmentRepository.save(CampusDutyAssignment.assignCoffee(
			command.campusId(),
			targetMember.userId()
		));
		return DutyAssignmentResult.of(assignment, getUserOrThrow(assignment.userId()));
	}

	@Transactional
	public void revokeCoffeeDuty(Long campusId, Long assignmentId, Long requesterId) {
		requireCampusManager(
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

	@Transactional(readOnly = true)
	public List<CampusMembershipResult> getMyCampuses(Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		return campusMemberRepository.findByUserIdAndStatusOrderByIdDesc(requester.userId(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> CampusMembershipResult.of(getCampusOrThrow(member.campusId()), member))
			.toList();
	}

	@Transactional(readOnly = true)
	public CampusDetailResult getCampus(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		Campus campus = getCampusOrThrow(campusId);
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campus.id(), requester.userId()).orElse(null);

		if (requester.isAdmin()) {
			return CampusDetailResult.of(campus, membership, true);
		}

		if (membership == null || !membership.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN);
		}

		return CampusDetailResult.of(campus, membership, membership.canViewInviteCode());
	}

	@Transactional
	public CampusDetailResult updateCampus(UpdateCampusCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		Campus campus = getCampusOrThrow(command.campusId());
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campus.id(), requester.userId()).orElse(null);

		if (!requester.isAdmin()) {
			if (membership == null || !membership.isActive()) {
				throw new BusinessException(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
			}
			CampusRolePolicy.requireCampusManager(membership, ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
		}

		campus.update(command.name(), command.region(), command.description(), command.isActive());
		return CampusDetailResult.of(campus, membership, requester.isAdmin() || (membership != null && membership.canViewInviteCode()));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
	}

	private void requireCampusManager(Long campusId, Long requesterId, ErrorCode errorCode, String message) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode, message));
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode, message);
	}

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}

	private String generateUniqueInviteCode() {
		for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
			String inviteCode = inviteCodeGenerator.generate();
			if (!campusRepository.existsByInviteCode(inviteCode)) {
				return inviteCode;
			}
		}
		throw new BusinessException(ErrorCode.CAMPUS_INVITE_CODE_GENERATION_FAILED);
	}

}
