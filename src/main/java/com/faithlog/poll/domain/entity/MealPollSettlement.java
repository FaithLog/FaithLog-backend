package com.faithlog.poll.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meal_poll_settlements")
public class MealPollSettlement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "poll_id", nullable = false, unique = true)
	private Long pollId;

	@Column(name = "payment_account_id", nullable = false)
	private Long paymentAccountId;

	@Column(name = "charged_by_user_id", nullable = false)
	private Long chargedByUserId;

	@Column(name = "requested_total_amount", nullable = false)
	private long requestedTotalAmount;

	@Column(name = "actual_total_amount", nullable = false)
	private long actualTotalAmount;

	@Column(name = "rounding_adjustment", nullable = false)
	private long roundingAdjustment;

	@Column(name = "charged_at", nullable = false)
	private Instant chargedAt;

	protected MealPollSettlement() {
	}

	private MealPollSettlement(
		Long campusId,
		Long pollId,
		Long paymentAccountId,
		Long chargedByUserId,
		long requestedTotalAmount,
		long actualTotalAmount,
		long roundingAdjustment,
		Instant chargedAt
	) {
		this.campusId = campusId;
		this.pollId = pollId;
		this.paymentAccountId = paymentAccountId;
		this.chargedByUserId = chargedByUserId;
		this.requestedTotalAmount = requestedTotalAmount;
		this.actualTotalAmount = actualTotalAmount;
		this.roundingAdjustment = roundingAdjustment;
		this.chargedAt = chargedAt;
	}

	public static MealPollSettlement create(
		Long campusId,
		Long pollId,
		Long paymentAccountId,
		Long chargedByUserId,
		long requestedTotalAmount,
		long actualTotalAmount,
		long roundingAdjustment,
		Instant chargedAt
	) {
		return new MealPollSettlement(
			campusId, pollId, paymentAccountId, chargedByUserId,
			requestedTotalAmount, actualTotalAmount, roundingAdjustment, chargedAt
		);
	}

	public Long id() { return id; }
	public Long campusId() { return campusId; }
	public Long pollId() { return pollId; }
	public Long paymentAccountId() { return paymentAccountId; }
	public Long chargedByUserId() { return chargedByUserId; }
	public long requestedTotalAmount() { return requestedTotalAmount; }
	public long actualTotalAmount() { return actualTotalAmount; }
	public long roundingAdjustment() { return roundingAdjustment; }
	public Instant chargedAt() { return chargedAt; }
}
