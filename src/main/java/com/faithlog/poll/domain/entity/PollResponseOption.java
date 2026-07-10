package com.faithlog.poll.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "poll_response_options",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_poll_response_options_response_option", columnNames = {"response_id", "option_id"})
	}
)
public class PollResponseOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "response_id", nullable = false)
	private Long responseId;

	@Column(name = "option_id", nullable = false)
	private Long optionId;

	protected PollResponseOption() {
	}

	private PollResponseOption(Long responseId, Long optionId) {
		this.responseId = responseId;
		this.optionId = optionId;
	}

	public static PollResponseOption create(Long responseId, Long optionId) {
		return new PollResponseOption(responseId, optionId);
	}

	public Long id() {
		return id;
	}

	public Long responseId() {
		return responseId;
	}

	public Long optionId() {
		return optionId;
	}
}
