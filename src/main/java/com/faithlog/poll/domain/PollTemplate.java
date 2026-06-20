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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "poll_templates")
public class PollTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "poll_type", nullable = false, length = 40)
	private PollType pollType;

	@Enumerated(EnumType.STRING)
	@Column(name = "selection_type", nullable = false, length = 30)
	private SelectionType selectionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "charge_generation_type", nullable = false, length = 40)
	private ChargeGenerationType chargeGenerationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_category", length = 30)
	private PaymentCategory paymentCategory;

	@Column(name = "payment_account_id")
	private Long paymentAccountId;

	@Column(name = "auto_create_enabled", nullable = false)
	private boolean autoCreateEnabled;

	@Column(name = "start_day_of_week", nullable = false)
	private int startDayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_day_of_week", nullable = false)
	private int endDayOfWeek;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "is_default", nullable = false)
	private boolean isDefault;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PollTemplate() {
	}

	private PollTemplate(
		Long campusId,
		String title,
		PollType pollType,
		SelectionType selectionType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		boolean autoCreateEnabled,
		DayOfWeek startDayOfWeek,
		LocalTime startTime,
		DayOfWeek endDayOfWeek,
		LocalTime endTime,
		boolean isDefault
	) {
		this.campusId = campusId;
		this.title = title;
		this.pollType = pollType;
		this.selectionType = selectionType;
		this.chargeGenerationType = chargeGenerationType;
		this.paymentCategory = paymentCategory;
		this.paymentAccountId = paymentAccountId;
		this.autoCreateEnabled = autoCreateEnabled;
		this.startDayOfWeek = startDayOfWeek.getValue();
		this.startTime = startTime;
		this.endDayOfWeek = endDayOfWeek.getValue();
		this.endTime = endTime;
		this.isDefault = isDefault;
		this.isActive = true;
	}

	public static PollTemplate create(
		Long campusId,
		String title,
		PollType pollType,
		SelectionType selectionType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		boolean autoCreateEnabled,
		DayOfWeek startDayOfWeek,
		LocalTime startTime,
		DayOfWeek endDayOfWeek,
		LocalTime endTime,
		boolean isDefault
	) {
		return new PollTemplate(
			campusId,
			title,
			pollType,
			selectionType,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			autoCreateEnabled,
			startDayOfWeek,
			startTime,
			endDayOfWeek,
			endTime,
			isDefault
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

	public void update(
		String title,
		SelectionType selectionType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		boolean autoCreateEnabled,
		DayOfWeek startDayOfWeek,
		LocalTime startTime,
		DayOfWeek endDayOfWeek,
		LocalTime endTime
	) {
		this.title = title;
		this.selectionType = selectionType;
		this.chargeGenerationType = chargeGenerationType;
		this.paymentCategory = paymentCategory;
		this.paymentAccountId = paymentAccountId;
		this.autoCreateEnabled = autoCreateEnabled;
		this.startDayOfWeek = startDayOfWeek.getValue();
		this.startTime = startTime;
		this.endDayOfWeek = endDayOfWeek.getValue();
		this.endTime = endTime;
	}

	public void deactivate() {
		this.isActive = false;
	}

	public void connectPaymentAccount(Long paymentAccountId) {
		this.paymentAccountId = paymentAccountId;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
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

	public ChargeGenerationType chargeGenerationType() {
		return chargeGenerationType;
	}

	public PaymentCategory paymentCategory() {
		return paymentCategory;
	}

	public Long paymentAccountId() {
		return paymentAccountId;
	}

	public boolean autoCreateEnabled() {
		return autoCreateEnabled;
	}

	public DayOfWeek startDayOfWeek() {
		return DayOfWeek.of(startDayOfWeek);
	}

	public LocalTime startTime() {
		return startTime;
	}

	public DayOfWeek endDayOfWeek() {
		return DayOfWeek.of(endDayOfWeek);
	}

	public LocalTime endTime() {
		return endTime;
	}

	public boolean isDefault() {
		return isDefault;
	}

	public boolean isActive() {
		return isActive;
	}
}
