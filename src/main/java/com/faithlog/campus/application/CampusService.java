package com.faithlog.campus.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusService {

	private static final int INVITE_CODE_MAX_ATTEMPTS = 20;

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final InviteCodeGenerator inviteCodeGenerator;

	public CampusService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		InviteCodeGenerator inviteCodeGenerator
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.inviteCodeGenerator = inviteCodeGenerator;
	}

	@Transactional
	public CampusCreateResult createCampus(CreateCampusCommand command) {
		User requester = getActiveUser(command.requesterId());
		if (!canCreateCampus(requester.role())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 생성 권한이 없습니다.");
		}

		Campus campus = campusRepository.save(Campus.create(
			command.name(),
			command.region(),
			command.description(),
			generateUniqueInviteCode()
		));
		CampusMember creatorMembership = campusMemberRepository.save(CampusMember.createMinister(campus.id(), requester.id()));
		return CampusCreateResult.of(campus, creatorMembership);
	}

	@Transactional
	public CampusMembershipResult joinCampus(JoinCampusCommand command) {
		User requester = getActiveUser(command.requesterId());
		Campus campus = campusRepository.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 초대코드입니다."));

		if (campusMemberRepository.existsByCampusIdAndUserId(campus.id(), requester.id())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 가입된 캠퍼스입니다.");
		}

		CampusMember member = campusMemberRepository.save(CampusMember.createMember(campus.id(), requester.id()));
		return CampusMembershipResult.of(campus, member);
	}

	@Transactional(readOnly = true)
	public List<CampusMembershipResult> getMyCampuses(Long requesterId) {
		User requester = getActiveUser(requesterId);
		return campusMemberRepository.findByUserIdAndStatusOrderByIdDesc(requester.id(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> CampusMembershipResult.of(getCampusOrThrow(member.campusId()), member))
			.toList();
	}

	@Transactional(readOnly = true)
	public CampusDetailResult getCampus(Long campusId, Long requesterId) {
		User requester = getActiveUser(requesterId);
		Campus campus = getCampusOrThrow(campusId);
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campus.id(), requester.id()).orElse(null);

		if (requester.role() == UserRole.ADMIN) {
			return CampusDetailResult.of(campus, membership, true);
		}

		if (membership == null || !membership.isActive()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "캠퍼스 조회 권한이 없습니다.");
		}

		return CampusDetailResult.of(campus, membership, membership.canViewInviteCode());
	}

	private User getActiveUser(Long userId) {
		User user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return user;
	}

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private boolean canCreateCampus(UserRole role) {
		return role == UserRole.MANAGER || role == UserRole.ADMIN;
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
