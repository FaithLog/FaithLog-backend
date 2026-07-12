package com.faithlog.user.service;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.result.CampusMembershipRow;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.service.result.CampusMembershipResult;
import com.faithlog.user.service.result.UserMeResult;
import org.springframework.stereotype.Component;

@Component
class CampusMembershipQuerySupport {

	private final CampusMemberRepositoryPort campusMemberRepository;

	CampusMembershipQuerySupport(CampusMemberRepositoryPort campusMemberRepository) {
		this.campusMemberRepository = campusMemberRepository;
	}

	UserMeResult toUserMeResult(User user) {
		return UserMeResult.from(user, campusMemberRepository
			.findMembershipRowsByUserIdAndStatusOrderByIdDesc(user.id(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(this::toResult)
			.toList());
	}

	private CampusMembershipResult toResult(CampusMembershipRow row) {
		return new CampusMembershipResult(
			row.membershipId(),
			row.campusId(),
			row.campusName(),
			row.region(),
			row.campusRole().name(),
			row.status().name()
		);
	}
}
