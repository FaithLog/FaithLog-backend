package com.faithlog.admin.service.query;

public record AdminCampusSearchCriteria(
	String name,
	String region,
	AdminCampusStatus status
) {
}
