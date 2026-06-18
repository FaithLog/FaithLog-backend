package com.faithlog.billing.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
	name = "charge_items",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_charge_items_source",
			columnNames = {"campus_id", "user_id", "payment_category", "source_type", "source_id"}
		)
	}
)
public class ChargeItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_category", nullable = false, length = 30)
	private PaymentCategory paymentCategory;

	@Column(name = "payment_account_id", nullable = false)
	private Long paymentAccountId;

	@Column(name = "bank_name_snapshot", nullable = false, length = 100)
	private String bankNameSnapshot;

	@Column(name = "account_number_snapshot", nullable = false, length = 100)
	private String accountNumberSnapshot;

	@Column(name = "account_holder_snapshot", nullable = false, length = 100)
	private String accountHolderSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private ChargeSourceType sourceType;

	@Column(name = "source_id", nullable = false)
	private Long sourceId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 255)
	private String reason;

	@Column(nullable = false)
	private int amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ChargeStatus status;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ChargeItem() {
	}

	private ChargeItem(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		String bankNameSnapshot,
		String accountNumberSnapshot,
		String accountHolderSnapshot,
		ChargeSourceType sourceType,
		Long sourceId,
		String title,
		String reason,
		int amount,
		LocalDate dueDate
	) {
		this.campusId = campusId;
		this.userId = userId;
		this.paymentCategory = paymentCategory;
		this.paymentAccountId = paymentAccountId;
		this.bankNameSnapshot = bankNameSnapshot;
		this.accountNumberSnapshot = accountNumberSnapshot;
		this.accountHolderSnapshot = accountHolderSnapshot;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.title = title;
		this.reason = reason;
		this.amount = amount;
		this.status = ChargeStatus.UNPAID;
		this.dueDate = dueDate;
	}

	public static ChargeItem create(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		String bankNameSnapshot,
		String accountNumberSnapshot,
		String accountHolderSnapshot,
		ChargeSourceType sourceType,
		Long sourceId,
		String title,
		String reason,
		int amount,
		LocalDate dueDate
	) {
		return new ChargeItem(
			campusId,
			userId,
			paymentCategory,
			paymentAccountId,
			bankNameSnapshot,
			accountNumberSnapshot,
			accountHolderSnapshot,
			sourceType,
			sourceId,
			title,
			reason,
			amount,
			dueDate
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

	public boolean isUnpaid() {
		return status == ChargeStatus.UNPAID;
	}

	public void reconnectPaymentAccount(PaymentAccount account) {
		if (!isUnpaid()) {
			return;
		}
		this.paymentAccountId = account.id();
		this.bankNameSnapshot = account.bankName();
		this.accountNumberSnapshot = account.accountNumber();
		this.accountHolderSnapshot = account.accountHolder();
	}

	public void markPaid() {
		this.status = ChargeStatus.PAID;
		this.paidAt = Instant.now();
	}

	public void markWaived() {
		this.status = ChargeStatus.WAIVED;
	}

	public void markCanceled() {
		this.status = ChargeStatus.CANCELED;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long userId() {
		return userId;
	}

	public PaymentCategory paymentCategory() {
		return paymentCategory;
	}

	public Long paymentAccountId() {
		return paymentAccountId;
	}

	public String bankNameSnapshot() {
		return bankNameSnapshot;
	}

	public String accountNumberSnapshot() {
		return accountNumberSnapshot;
	}

	public String accountHolderSnapshot() {
		return accountHolderSnapshot;
	}

	public ChargeSourceType sourceType() {
		return sourceType;
	}

	public Long sourceId() {
		return sourceId;
	}

	public String title() {
		return title;
	}

	public String reason() {
		return reason;
	}

	public int amount() {
		return amount;
	}

	public ChargeStatus status() {
		return status;
	}

	public LocalDate dueDate() {
		return dueDate;
	}
}
