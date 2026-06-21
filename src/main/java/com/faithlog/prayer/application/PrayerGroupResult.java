package com.faithlog.prayer.application;

import com.faithlog.prayer.domain.PrayerGroup;
import java.util.List;

public record PrayerGroupResult(
	Long groupId,
	Long seasonId,
	String name,
	int sortOrder,
	boolean active,
	List<PrayerGroupMemberResult> members
) {

	public static PrayerGroupResult of(PrayerGroup group, List<PrayerGroupMemberResult> members) {
		return new PrayerGroupResult(
			group.id(),
			group.seasonId(),
			group.name(),
			group.sortOrder(),
			group.isActive(),
			members
		);
	}
}
