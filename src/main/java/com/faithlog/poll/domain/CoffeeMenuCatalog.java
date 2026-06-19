package com.faithlog.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "coffee_menu_catalog",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_coffee_menu_catalog_brand_menu", columnNames = {"brand_id", "menu_code"})
	}
)
public class CoffeeMenuCatalog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "brand_id", nullable = false)
	private Long brandId;

	@Column(name = "menu_code", nullable = false, length = 100)
	private String menuCode;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "price_amount", nullable = false)
	private int priceAmount;

	@Column(nullable = false, length = 60)
	private String category;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CoffeeMenuCatalog() {
	}

	private CoffeeMenuCatalog(Long brandId, String menuCode, String name, int priceAmount, String category, int sortOrder, boolean active) {
		this.brandId = brandId;
		this.menuCode = menuCode;
		this.name = name;
		this.priceAmount = priceAmount;
		this.category = category;
		this.sortOrder = sortOrder;
		this.isActive = active;
	}

	public static CoffeeMenuCatalog create(Long brandId, String menuCode, String name, int priceAmount, String category, int sortOrder, boolean active) {
		return new CoffeeMenuCatalog(brandId, menuCode, name, priceAmount, category, sortOrder, active);
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

	public void updateSeed(String name, int priceAmount, String category, int sortOrder, boolean active) {
		this.name = name;
		this.priceAmount = priceAmount;
		this.category = category;
		this.sortOrder = sortOrder;
		this.isActive = active;
	}

	public void deactivate() {
		this.isActive = false;
	}

	public Long id() {
		return id;
	}

	public Long brandId() {
		return brandId;
	}

	public String menuCode() {
		return menuCode;
	}

	public String name() {
		return name;
	}

	public int priceAmount() {
		return priceAmount;
	}

	public String category() {
		return category;
	}

	public boolean isActive() {
		return isActive;
	}

	public int sortOrder() {
		return sortOrder;
	}
}
