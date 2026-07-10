package com.faithlog.admin.service.result;

import com.faithlog.admin.service.query.AdminCampusStatus;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import java.util.List;

public record AdminCampusResult(
	Long campusId,
	String name,
	String region,
	boolean isActive,
	String status,
	long memberCount,
	long adminCount
) {

	public static AdminCampusResult of(Campus campus, List<CampusMember> activeMembers) {
		return new AdminCampusResult(
			campus.id(),
			campus.name(),
			campus.region(),
			campus.isActive(),
			campus.isActive() ? AdminCampusStatus.ACTIVE.name() : AdminCampusStatus.PAUSED.name(),
			activeMembers.size(),
			activeMembers.stream().filter(member -> member.campusRole().canManageCampusMembers()).count()
		);
	}
}
