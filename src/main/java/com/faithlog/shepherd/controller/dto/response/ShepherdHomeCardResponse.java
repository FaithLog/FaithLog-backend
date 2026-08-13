package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdHomeCardResult;
import java.time.LocalDate;
import java.util.List;

public record ShepherdHomeCardResponse(
	boolean visible,
	String title,
	LocalDate serviceDate,
	long assignedGroupCount,
	long submittedGroupCount,
	List<ShepherdHomeGroupResponse> groups
) {

	public static ShepherdHomeCardResponse from(ShepherdHomeCardResult result) {
		return new ShepherdHomeCardResponse(
			result.visible(),
			result.title(),
			result.serviceDate(),
			result.assignedGroupCount(),
			result.submittedGroupCount(),
			result.groups().stream().map(ShepherdHomeGroupResponse::from).toList()
		);
	}
}
