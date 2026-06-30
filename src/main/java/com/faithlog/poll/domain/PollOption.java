package com.faithlog.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "poll_options")
public class PollOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "poll_id", nullable = false)
	private Long pollId;

	@Column(nullable = false, length = 200)
	private String content;

	@Column(name = "compose_menu_code", length = 100)
	private String composeMenuCode;

	@Column(name = "price_amount", nullable = false)
	private int priceAmount;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "user_added", nullable = false)
	private boolean userAdded;

	@Column(name = "created_by_user_id")
	private Long createdByUserId;

	protected PollOption() {
	}

	private PollOption(
		Long pollId,
		String content,
		String composeMenuCode,
		int priceAmount,
		int sortOrder,
		boolean userAdded,
		Long createdByUserId
	) {
		this.pollId = pollId;
		this.content = content;
		this.composeMenuCode = composeMenuCode;
		this.priceAmount = priceAmount;
		this.sortOrder = sortOrder;
		this.userAdded = userAdded;
		this.createdByUserId = createdByUserId;
	}

	public static PollOption create(Long pollId, String content, String composeMenuCode, int priceAmount, int sortOrder) {
		return new PollOption(pollId, content, composeMenuCode, priceAmount, sortOrder, false, null);
	}

	public static PollOption createUserAdded(Long pollId, String content, int sortOrder, Long createdByUserId) {
		return new PollOption(pollId, content, null, 0, sortOrder, true, createdByUserId);
	}

	public static PollOption createUserAdded(
		Long pollId,
		String content,
		String composeMenuCode,
		int priceAmount,
		int sortOrder,
		Long createdByUserId
	) {
		return new PollOption(pollId, content, composeMenuCode, priceAmount, sortOrder, true, createdByUserId);
	}

	public Long id() {
		return id;
	}

	public Long pollId() {
		return pollId;
	}

	public String content() {
		return content;
	}

	public String composeMenuCode() {
		return composeMenuCode;
	}

	public int priceAmount() {
		return priceAmount;
	}

	public int sortOrder() {
		return sortOrder;
	}

	public boolean userAdded() {
		return userAdded;
	}

	public Long createdByUserId() {
		return createdByUserId;
	}
}
