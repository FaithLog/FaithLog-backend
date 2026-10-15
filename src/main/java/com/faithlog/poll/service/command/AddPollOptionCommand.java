package com.faithlog.poll.service.command;

public record AddPollOptionCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	String content,
	Long menuId,
	Integer priceAmount
) {

	public AddPollOptionCommand(Long campusId, Long pollId, Long requesterId, String content, Long menuId) {
		this(campusId, pollId, requesterId, content, menuId, null);
	}

	public AddPollOptionCommand(Long campusId, Long pollId, Long requesterId, String content) {
		this(campusId, pollId, requesterId, content, null);
	}
}
