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
		if (!requester.canCreateCampus()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 생성 권한이 없습니다.");
		}

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
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 초대코드입니다."));

		CampusMember existingMember = campusMemberRepository.findByCampusIdAndUserId(campus.id(), requester.userId()).orElse(null);
		if (existingMember != null && existingMember.isActive()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 가입된 캠퍼스입니다.");
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
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "캠퍼스 멤버를 찾을 수 없습니다."));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(campusId, requester.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 멤버 관리 권한이 없습니다."));
			if (!requesterMembership.canManageCampusMembers()) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 멤버 관리 권한이 없습니다.");
			}
		}

		targetMember.deactivate();
	}

	@Transactional
	public AdminCampusMemberResult changeCampusRole(ChangeCampusRoleCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndId(command.campusId(), command.campusMemberId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "캠퍼스 멤버를 찾을 수 없습니다."));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(command.campusId(), requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 역할 변경 권한이 없습니다."));
			if (!requesterMembership.canManageCampusMembers()) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 역할 변경 권한이 없습니다.");
			}
			if (!requesterMembership.campusRole().canChangeCampusRole(targetMember.campusRole(), command.campusRole())) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "상위 캠퍼스 역할은 변경할 수 없습니다.");
			}
		}

		targetMember.changeCampusRole(command.campusRole());
		return AdminCampusMemberResult.of(targetMember, getUserOrThrow(targetMember.userId()));
	}

	@Transactional(readOnly = true)
	public List<AdminCampusMemberResult> getCampusMembers(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, "캠퍼스 멤버 관리 권한이 없습니다.");
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> AdminCampusMemberResult.of(member, getUserOrThrow(member.userId())))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<DutyAssignmentResult> getDutyAssignments(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, "커피 담당자 관리 권한이 없습니다.");
		return dutyAssignmentRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(assignment -> DutyAssignmentResult.of(assignment, getUserOrThrow(assignment.userId())))
			.toList();
	}

	@Transactional
	public DutyAssignmentResult assignCoffeeDuty(AssignCoffeeDutyCommand command) {
		requireCampusManager(command.campusId(), command.requesterId(), "커피 담당자 관리 권한이 없습니다.");
		lockCampusOrThrow(command.campusId());
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndUserId(command.campusId(), command.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.NOT_FOUND,
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
		requireCampusManager(campusId, requesterId, "커피 담당자 관리 권한이 없습니다.");
		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndId(campusId, DutyType.COFFEE, assignmentId)
			.filter(CampusDutyAssignment::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "커피 담당자 배정을 찾을 수 없습니다."));
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
			throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 조회 권한이 없습니다.");
		}

		return CampusDetailResult.of(campus, membership, membership.canViewInviteCode());
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return user;
	}

	private CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private void requireCampusManager(Long campusId, Long requesterId, String message) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, message));
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, message);
		}
	}

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private String generateUniqueInviteCode() {
		for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
			String inviteCode = inviteCodeGenerator.generate();
			if (!campusRepository.existsByInviteCode(inviteCode)) {
				return inviteCode;
			}
		}
		throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "초대코드 생성에 실패했습니다.");
	}
}
