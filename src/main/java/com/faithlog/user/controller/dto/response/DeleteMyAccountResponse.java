package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.DeleteMyAccountResult;
import java.time.Instant;

public record DeleteMyAccountResponse(
	Instant deletedAt
) {

	public static DeleteMyAccountResponse from(DeleteMyAccountResult result) {
		return new DeleteMyAccountResponse(result.deletedAt());
	}
}
