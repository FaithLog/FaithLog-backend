package com.faithlog.user.service.command;

public record UpdateMyNameCommand(
	Long userId,
	String name
) {
}
