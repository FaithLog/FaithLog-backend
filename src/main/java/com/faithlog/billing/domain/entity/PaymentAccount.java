package com.faithlog.billing.domain.entity;

import com.faithlog.billing.domain.type.PaymentCategory;
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
@Table(name = "payment_accounts")
public class PaymentAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 30)
	private PaymentCategory accountType;

	@Column(nullable = false, length = 100)
	private String nickname;

	@Column(name = "bank_name", nullable = false, length = 100)
	private String bankName;

	@Column(name = "account_number", nullable = false, length = 100)
	private String accountNumber;

	@Column(name = "account_holder", nullable = false, length = 100)
	private String accountHolder;

	@Column(name = "owner_user_id")
	private Long ownerUserId;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentAccount() {
	}

	private PaymentAccount(
		Long campusId,
		PaymentCategory accountType,
		String nickname,
		String bankName,
		String accountNumber,
		String accountHolder,
		Long ownerUserId
	) {
		this.campusId = campusId;
		this.accountType = accountType;
		this.nickname = nickname;
		this.bankName = bankName;
		this.accountNumber = accountNumber;
		this.accountHolder = accountHolder;
		this.ownerUserId = ownerUserId;
		this.isActive = true;
	}

	public static PaymentAccount create(
		Long campusId,
		PaymentCategory accountType,
		String nickname,
		String bankName,
		String accountNumber,
		String accountHolder,
		Long ownerUserId
	) {
		return new PaymentAccount(campusId, accountType, nickname, bankName, accountNumber, accountHolder, ownerUserId);
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

	public void deactivate() {
		if (!isActive) {
			return;
		}
		this.isActive = false;
		this.deactivatedAt = Instant.now();
	}

	public void activate() {
		if (isActive) {
			return;
		}
		this.isActive = true;
		this.deactivatedAt = null;
	}

	public void softDelete() {
		this.deletedAt = Instant.now();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public PaymentCategory accountType() {
		return accountType;
	}

	public String nickname() {
		return nickname;
	}

	public String bankName() {
		return bankName;
	}

	public String accountNumber() {
		return accountNumber;
	}

	public String accountHolder() {
		return accountHolder;
	}

	public Long ownerUserId() {
		return ownerUserId;
	}

	public boolean isActive() {
		return isActive;
	}

	public Instant deactivatedAt() {
		return deactivatedAt;
	}

	public Instant deletedAt() {
		return deletedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
