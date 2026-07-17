package com.faithlog.campus.service.command;

public record JoinCampusCommand(
	Long requesterId,
	String inviteCode
) {
}
