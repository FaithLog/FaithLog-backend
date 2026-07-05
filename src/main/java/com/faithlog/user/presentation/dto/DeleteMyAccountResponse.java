package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.DeleteMyAccountResult;
import java.time.Instant;

public record DeleteMyAccountResponse(
	Instant deletedAt
) {

	public static DeleteMyAccountResponse from(DeleteMyAccountResult result) {
		return new DeleteMyAccountResponse(result.deletedAt());
	}
}
