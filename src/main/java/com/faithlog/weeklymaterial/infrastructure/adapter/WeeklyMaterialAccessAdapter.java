package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAccessPort;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialAccessAdapter implements WeeklyMaterialAccessPort {
	private final CampusAccessPolicy campusAccess;
	private final CampusMemberRepositoryPort members;

	public WeeklyMaterialAccessAdapter(CampusAccessPolicy campusAccess, CampusMemberRepositoryPort members) {
		this.campusAccess = campusAccess;
		this.members = members;
	}

	@Override
	public void requireManager(Long campusId, Long requesterId) {
		campusAccess.requireCampusManager(campusId, requesterId,
			ErrorCode.WEEKLY_MATERIAL_MANAGE_FORBIDDEN, ErrorCode.WEEKLY_MATERIAL_MANAGE_FORBIDDEN.message());
	}

	@Override
	public void requireActiveMember(Long campusId, Long requesterId) {
		var user = campusAccess.getActiveUser(requesterId);
		members.findByCampusIdAndUserId(campusId, user.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.WEEKLY_MATERIAL_ACCESS_FORBIDDEN));
	}
}
