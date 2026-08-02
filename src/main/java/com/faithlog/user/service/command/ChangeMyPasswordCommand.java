package com.faithlog.user.service.command;

public record ChangeMyPasswordCommand(
	Long userId,
	String currentPassword,
	String newPassword
) {
}
