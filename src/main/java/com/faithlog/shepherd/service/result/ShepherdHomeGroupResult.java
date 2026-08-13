package com.faithlog.shepherd.service.result;

public record ShepherdHomeGroupResult(
	Long groupId,
	String groupName,
	ShepherdHomeReportResult report
) {
}
