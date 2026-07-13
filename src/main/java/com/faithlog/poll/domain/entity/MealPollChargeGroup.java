package com.faithlog.poll.domain.entity;

import com.faithlog.poll.domain.type.MealChargeCalculationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_poll_charge_groups")
public class MealPollChargeGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "settlement_id", nullable = false)
	private Long settlementId;

	@Column(name = "poll_id", nullable = false)
	private Long pollId;

	@Column(name = "option_id", nullable = false)
	private Long optionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "calculation_type", nullable = false, length = 30)
	private MealChargeCalculationType calculationType;

	@Column(name = "entered_amount", nullable = false)
	private long enteredAmount;

	@Column(name = "response_count_snapshot", nullable = false)
	private int responseCountSnapshot;

	@Column(name = "amount_per_member", nullable = false)
	private int amountPerMember;

	@Column(name = "requested_total_amount", nullable = false)
	private long requestedTotalAmount;

	@Column(name = "actual_total_amount", nullable = false)
	private long actualTotalAmount;

	@Column(name = "rounding_adjustment", nullable = false)
	private long roundingAdjustment;

	protected MealPollChargeGroup() {
	}

	private MealPollChargeGroup(
		Long settlementId, Long pollId, Long optionId, MealChargeCalculationType calculationType,
		long enteredAmount, int responseCountSnapshot, int amountPerMember,
		long requestedTotalAmount, long actualTotalAmount, long roundingAdjustment
	) {
		this.settlementId = settlementId;
		this.pollId = pollId;
		this.optionId = optionId;
		this.calculationType = calculationType;
		this.enteredAmount = enteredAmount;
		this.responseCountSnapshot = responseCountSnapshot;
		this.amountPerMember = amountPerMember;
		this.requestedTotalAmount = requestedTotalAmount;
		this.actualTotalAmount = actualTotalAmount;
		this.roundingAdjustment = roundingAdjustment;
	}

	public static MealPollChargeGroup create(
		Long settlementId, Long pollId, Long optionId, MealChargeCalculationType calculationType,
		long enteredAmount, int responseCountSnapshot, int amountPerMember,
		long requestedTotalAmount, long actualTotalAmount, long roundingAdjustment
	) {
		return new MealPollChargeGroup(
			settlementId, pollId, optionId, calculationType, enteredAmount, responseCountSnapshot,
			amountPerMember, requestedTotalAmount, actualTotalAmount, roundingAdjustment
		);
	}

	public Long id() { return id; }
	public Long settlementId() { return settlementId; }
	public Long pollId() { return pollId; }
	public Long optionId() { return optionId; }
	public MealChargeCalculationType calculationType() { return calculationType; }
	public long enteredAmount() { return enteredAmount; }
	public int responseCountSnapshot() { return responseCountSnapshot; }
	public int amountPerMember() { return amountPerMember; }
	public long requestedTotalAmount() { return requestedTotalAmount; }
	public long actualTotalAmount() { return actualTotalAmount; }
	public long roundingAdjustment() { return roundingAdjustment; }
}
