package com.faithlog.user.service.command;

public record CompletePasswordResetCommand(String resetToken, String newPassword) {
}
