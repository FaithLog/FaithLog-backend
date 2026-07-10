package com.faithlog.campus.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;

public record MyDutyAssignmentResponse(
	Long userId,
	Long campusId,
	String dutyType,
	@JsonProperty("isActive") boolean active
) {

	public static MyDutyAssignmentResponse from(MyDutyAssignmentResult result) {
		return new MyDutyAssignmentResponse(
			result.userId(),
			result.campusId(),
			result.dutyType(),
			result.active()
		);
	}
}
