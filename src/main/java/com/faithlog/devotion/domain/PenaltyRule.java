package com.faithlog.devotion.domain;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "penalty_rules")
public class PenaltyRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 30)
	private PenaltyRuleType ruleType;

	@Enumerated(EnumType.STRING)
	@Column(name = "calculation_type", nullable = false, length = 30)
	private PenaltyCalculationType calculationType;

	@Column(name = "required_count", nullable = false)
	private int requiredCount;

	@Column(name = "base_amount", nullable = false)
	private int baseAmount;

	@Column(name = "amount_per_unit", nullable = false)
	private int amountPerUnit;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PenaltyRule() {
	}

	private PenaltyRule(
		Long campusId,
		PenaltyRuleType ruleType,
		PenaltyCalculationType calculationType,
		int requiredCount,
		int baseAmount,
		int amountPerUnit
	) {
		validateTypePair(ruleType, calculationType);
		validateNonNegative(requiredCount, baseAmount, amountPerUnit);
		this.campusId = campusId;
		this.ruleType = ruleType;
		this.calculationType = calculationType;
		this.requiredCount = requiredCount;
		this.baseAmount = baseAmount;
		this.amountPerUnit = amountPerUnit;
		this.isActive = true;
	}

	public static PenaltyRule create(
		Long campusId,
		PenaltyRuleType ruleType,
		PenaltyCalculationType calculationType,
		int requiredCount,
		int baseAmount,
		int amountPerUnit
	) {
		return new PenaltyRule(campusId, ruleType, calculationType, requiredCount, baseAmount, amountPerUnit);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void update(int requiredCount, int baseAmount, int amountPerUnit, boolean isActive) {
		validateNonNegative(requiredCount, baseAmount, amountPerUnit);
		this.requiredCount = requiredCount;
		this.baseAmount = baseAmount;
		this.amountPerUnit = amountPerUnit;
		this.isActive = isActive;
	}

	public void deactivate() {
		this.isActive = false;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public PenaltyRuleType ruleType() {
		return ruleType;
	}

	public PenaltyCalculationType calculationType() {
		return calculationType;
	}

	public int requiredCount() {
		return requiredCount;
	}

	public int baseAmount() {
		return baseAmount;
	}

	public int amountPerUnit() {
		return amountPerUnit;
	}

	public boolean isActive() {
		return isActive;
	}

	private static void validateTypePair(PenaltyRuleType ruleType, PenaltyCalculationType calculationType) {
		boolean valid = switch (ruleType) {
			case QUIET_TIME, PRAYER, BIBLE_READING -> calculationType == PenaltyCalculationType.MISSING_COUNT;
			case SATURDAY_LATE -> calculationType == PenaltyCalculationType.LATE_MINUTE;
		};
		if (!valid) {
			throw new BusinessException(ErrorCode.DEVOTION_PENALTY_RULE_INVALID_TYPE_PAIR);
		}
	}

	private static void validateNonNegative(int requiredCount, int baseAmount, int amountPerUnit) {
		if (requiredCount < 0 || baseAmount < 0 || amountPerUnit < 0) {
			throw new BusinessException(ErrorCode.DEVOTION_PENALTY_RULE_INVALID_VALUE);
		}
	}
}
