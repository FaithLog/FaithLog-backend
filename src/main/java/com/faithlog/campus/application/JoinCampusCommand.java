package com.faithlog.campus.application;

public record JoinCampusCommand(
	Long requesterId,
	String inviteCode
) {
}
