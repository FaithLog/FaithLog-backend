package com.faithlog.admin.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
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
