package com.faithlog.poll.application;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.CoffeeMenuCatalog;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollTemplateOption;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class DefaultCoffeePollTemplateProvisioner {

	private final CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;
	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;

	public DefaultCoffeePollTemplateProvisioner(
		CoffeeMenuCatalogRepository coffeeMenuCatalogRepository,
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository
	) {
		this.coffeeMenuCatalogRepository = coffeeMenuCatalogRepository;
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
	}

	public PollTemplate provision(Long campusId) {
		return pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campusId, PollType.COFFEE)
			.orElseGet(() -> createDefaultTemplate(campusId));
	}

	private PollTemplate createDefaultTemplate(Long campusId) {
		PollTemplate template = pollTemplateRepository.save(PollTemplate.create(
			campusId,
			"커피 주문 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			null,
			true,
			false,
			DayOfWeek.SUNDAY,
			LocalTime.of(20, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			true
		));
		List<PollTemplateOption> options = List.of(
			option(template.id(), "아이스 아메리카노", "AMERICANO_ICE", 1),
			option(template.id(), "아메리카노", "AMERICANO_HOT", 2),
			option(template.id(), "아이스티", "ICED_TEA", 3),
			option(template.id(), "아이스 라떼", "CAFE_LATTE", 4),
			option(template.id(), "라떼", "CAFE_LATTE", 5)
		);
		pollTemplateOptionRepository.saveAll(options);
		return template;
	}

	private PollTemplateOption option(Long templateId, String content, String menuCode, int sortOrder) {
		CoffeeMenuCatalog menu = coffeeMenuCatalogRepository.findByMenuCode(menuCode)
			.filter(CoffeeMenuCatalog::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_MENU_NOT_FOUND));
		return PollTemplateOption.create(templateId, content, menu.menuCode(), menu.priceAmount(), sortOrder);
	}
}
