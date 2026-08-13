package com.faithlog.shepherd.service.result;

import java.time.LocalDate;
import java.util.List;

public record ShepherdHomeCardResult(
	boolean visible,
	String title,
	LocalDate serviceDate,
	long assignedGroupCount,
	long submittedGroupCount,
	List<ShepherdHomeGroupResult> groups
) {

	private static final String TITLE = "이번 주 목홀타를 입력해 주세요";

	public static ShepherdHomeCardResult hidden() {
		return new ShepherdHomeCardResult(false, null, null, 0, 0, List.of());
	}

	public static ShepherdHomeCardResult visible(LocalDate serviceDate, List<ShepherdHomeGroupResult> groups) {
		long submittedCount = groups.stream()
			.filter(group -> group.report() != null && "SUBMITTED".equals(group.report().status()))
			.count();
		return new ShepherdHomeCardResult(true, TITLE, serviceDate, groups.size(), submittedCount, groups);
	}
}
