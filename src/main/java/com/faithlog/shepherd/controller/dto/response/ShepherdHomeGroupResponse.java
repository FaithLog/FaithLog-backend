package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdHomeGroupResult;

public record ShepherdHomeGroupResponse(
	Long groupId,
	String groupName,
	ShepherdHomeReportResponse report
) {

	public static ShepherdHomeGroupResponse from(ShepherdHomeGroupResult result) {
		return new ShepherdHomeGroupResponse(
			result.groupId(),
			result.groupName(),
			result.report() == null ? null : ShepherdHomeReportResponse.from(result.report())
		);
	}
}
