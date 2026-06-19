package com.faithlog.admin.application;

public record AdminCampusSearchCriteria(
	String name,
	String region,
	AdminCampusStatus status
) {
}
