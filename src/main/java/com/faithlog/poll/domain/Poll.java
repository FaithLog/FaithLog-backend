package com.faithlog.poll.domain;

import com.faithlog.billing.domain.PaymentCategory;
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
@Table(name = "polls")
public class Poll {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "template_id")
	private Long templateId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "poll_type", nullable = false, length = 40)
	private PollType pollType;

	@Enumerated(EnumType.STRING)
	@Column(name = "selection_type", nullable = false, length = 30)
	private SelectionType selectionType;

	@Column(name = "is_anonymous", nullable = false)
	private boolean isAnonymous;

	@Enumerated(EnumType.STRING)
	@Column(name = "charge_generation_type", nullable = false, length = 40)
	private ChargeGenerationType chargeGenerationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_category", length = 30)
	private PaymentCategory paymentCategory;

	@Column(name = "payment_account_id")
	private Long paymentAccountId;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PollStatus status;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Poll() {
	}

	private Poll(
		Long campusId,
		Long templateId,
		String title,
		PollType pollType,
		SelectionType selectionType,
		boolean isAnonymous,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Instant startsAt,
		Instant endsAt,
		Long createdBy
	) {
		this.campusId = campusId;
		this.templateId = templateId;
		this.title = title;
		this.pollType = pollType;
		this.selectionType = selectionType;
		this.isAnonymous = isAnonymous;
		this.chargeGenerationType = chargeGenerationType;
		this.paymentCategory = paymentCategory;
		this.paymentAccountId = paymentAccountId;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.status = PollStatus.SCHEDULED;
		this.createdBy = createdBy;
	}

	public static Poll create(
		Long campusId,
		Long templateId,
		String title,
		PollType pollType,
		SelectionType selectionType,
		boolean isAnonymous,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Instant startsAt,
		Instant endsAt,
		Long createdBy
	) {
		return new Poll(
			campusId,
			templateId,
			title,
			pollType,
			selectionType,
			isAnonymous,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			startsAt,
			endsAt,
			createdBy
		);
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

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long templateId() {
		return templateId;
	}

	public String title() {
		return title;
	}

	public PollType pollType() {
		return pollType;
	}

	public SelectionType selectionType() {
		return selectionType;
	}

	public boolean isAnonymous() {
		return isAnonymous;
	}

	public ChargeGenerationType chargeGenerationType() {
		return chargeGenerationType;
	}

	public PaymentCategory paymentCategory() {
		return paymentCategory;
	}

	public Long paymentAccountId() {
		return paymentAccountId;
	}

	public Instant startsAt() {
		return startsAt;
	}

	public Instant endsAt() {
		return endsAt;
	}

	public PollStatus status() {
		return status;
	}
}
