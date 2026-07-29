package com.faithlog.user.service.command;

public record ConfirmEmailVerificationCommand(String email, String code) {
}
