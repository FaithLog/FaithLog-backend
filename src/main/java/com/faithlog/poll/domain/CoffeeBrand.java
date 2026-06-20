package com.faithlog.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "coffee_brands")
public class CoffeeBrand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "brand_code", nullable = false, unique = true, length = 100)
	private String brandCode;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CoffeeBrand() {
	}

	private CoffeeBrand(String brandCode, String name, int sortOrder) {
		this.brandCode = brandCode;
		this.name = name;
		this.isActive = true;
		this.sortOrder = sortOrder;
	}

	public static CoffeeBrand create(String brandCode, String name, int sortOrder) {
		return new CoffeeBrand(brandCode, name, sortOrder);
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

	public void updateSeed(String name, int sortOrder, boolean active) {
		this.name = name;
		this.sortOrder = sortOrder;
		this.isActive = active;
	}

	public Long id() {
		return id;
	}

	public String brandCode() {
		return brandCode;
	}

	public String name() {
		return name;
	}

	public boolean isActive() {
		return isActive;
	}

	public int sortOrder() {
		return sortOrder;
	}
}
