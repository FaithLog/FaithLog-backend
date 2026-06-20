package com.faithlog.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "poll_template_options")
public class PollTemplateOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "template_id", nullable = false)
	private Long templateId;

	@Column(nullable = false, length = 200)
	private String content;

	@Column(name = "compose_menu_code", length = 100)
	private String composeMenuCode;

	@Column(name = "price_amount", nullable = false)
	private int priceAmount;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected PollTemplateOption() {
	}

	private PollTemplateOption(Long templateId, String content, String composeMenuCode, int priceAmount, int sortOrder) {
		this.templateId = templateId;
		this.content = content;
		this.composeMenuCode = composeMenuCode;
		this.priceAmount = priceAmount;
		this.sortOrder = sortOrder;
	}

	public static PollTemplateOption create(Long templateId, String content, String composeMenuCode, int priceAmount, int sortOrder) {
		return new PollTemplateOption(templateId, content, composeMenuCode, priceAmount, sortOrder);
	}

	public Long id() {
		return id;
	}

	public Long templateId() {
		return templateId;
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
}
