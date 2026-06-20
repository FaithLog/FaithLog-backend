package com.faithlog.poll.application;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.CoffeeMenuCatalog;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class PollOptionSnapshotResolver {

	private final CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;

	PollOptionSnapshotResolver(CoffeeMenuCatalogRepository coffeeMenuCatalogRepository) {
		this.coffeeMenuCatalogRepository = coffeeMenuCatalogRepository;
	}

	List<PollOptionSnapshot> resolveTemplateOptions(List<CreatePollTemplateOptionCommand> commands) {
		List<PollOptionSnapshot> snapshots = new ArrayList<>();
		for (CreatePollTemplateOptionCommand command : commands) {
			snapshots.add(resolve(command.content(), command.menuId(), command.priceAmount(), command.sortOrder()));
		}
		return sortAndValidate(snapshots);
	}

	List<PollOptionSnapshot> resolvePollOptions(List<CreatePollOptionCommand> commands) {
		List<PollOptionSnapshot> snapshots = new ArrayList<>();
		for (CreatePollOptionCommand command : commands) {
			snapshots.add(resolve(command.content(), command.menuId(), command.priceAmount(), command.sortOrder()));
		}
		return sortAndValidate(snapshots);
	}

	private PollOptionSnapshot resolve(String content, Long menuId, Integer priceAmount, int sortOrder) {
		if (menuId == null) {
			if (content == null || content.isBlank()) {
				throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
			}
			return new PollOptionSnapshot(content, null, priceAmount == null ? 0 : priceAmount, sortOrder);
		}

		CoffeeMenuCatalog menu = coffeeMenuCatalogRepository.findById(menuId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_MENU_NOT_FOUND));
		if (!menu.isActive()) {
			throw new BusinessException(ErrorCode.POLL_MENU_INACTIVE);
		}
		String snapshotContent = (content == null || content.isBlank()) ? menu.name() : content;
		return new PollOptionSnapshot(snapshotContent, menu.menuCode(), menu.priceAmount(), sortOrder);
	}

	private List<PollOptionSnapshot> sortAndValidate(List<PollOptionSnapshot> snapshots) {
		if (snapshots == null || snapshots.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		return snapshots.stream()
			.sorted(Comparator.comparingInt(PollOptionSnapshot::sortOrder))
			.toList();
	}
}
