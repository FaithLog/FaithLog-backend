package com.faithlog.weeklymaterial.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAccessPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialQueryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRow;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialFileResult;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WeeklyMaterialQueryService {
	private final WeeklyMaterialQueryPort queries;
	private final WeeklyMaterialAccessPort access;
	private final Clock clock;

	public WeeklyMaterialQueryService(WeeklyMaterialQueryPort queries, WeeklyMaterialAccessPort access, Clock clock) {
		this.queries = queries;
		this.access = access;
		this.clock = clock;
	}

	public WeeklyMaterialWeekResult getCurrent(Long campusId, Long requesterId) {
		return getWeek(campusId, requesterId, WeeklyMaterialWeek.currentMonday(clock));
	}

	public WeeklyMaterialWeekResult getWeek(Long campusId, Long requesterId, LocalDate weekStartDate) {
		access.requireActiveMember(campusId, requesterId);
		return getWeekAfterAuthorization(campusId, weekStartDate);
	}

	public WeeklyMaterialWeekResult getWeekForManager(Long campusId, Long requesterId, LocalDate weekStartDate) {
		access.requireManager(campusId, requesterId);
		return getWeekAfterAuthorization(campusId, weekStartDate);
	}

	private WeeklyMaterialWeekResult getWeekAfterAuthorization(Long campusId, LocalDate weekStartDate) {
		LocalDate week;
		try {
			week = WeeklyMaterialWeek.requireMonday(weekStartDate);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.WEEKLY_MATERIAL_INVALID_WEEK_START_DATE);
		}
		List<WeeklyMaterialRow> rows = queries.findActiveRows(List.of(week));
		return assemble(week, rows);
	}

	public Page<WeeklyMaterialWeekResult> list(Long campusId, Long requesterId, int year, int page, int size) {
		access.requireActiveMember(campusId, requesterId);
		if (page < 0) throw new BusinessException(ErrorCode.WEEKLY_MATERIAL_INVALID_PAGE);
		if (size < 1 || size > 100) throw new BusinessException(ErrorCode.WEEKLY_MATERIAL_INVALID_SIZE);
		LocalDate from;
		try {
			if (year <= 0) throw new DateTimeException("invalid year");
			from = LocalDate.of(year, 1, 1);
		} catch (DateTimeException exception) {
			throw new BusinessException(ErrorCode.WEEKLY_MATERIAL_INVALID_YEAR);
		}
		PageRequest pageable = PageRequest.of(page, size);
		Page<LocalDate> weeks = queries.findActiveWeekDates(from, from.plusYears(1), pageable);
		if (weeks.isEmpty()) return new PageImpl<>(List.of(), pageable, weeks.getTotalElements());
		List<WeeklyMaterialRow> rows = queries.findActiveRows(weeks.getContent());
		Map<LocalDate, List<WeeklyMaterialRow>> byWeek = rows.stream()
			.collect(Collectors.groupingBy(WeeklyMaterialRow::weekStartDate));
		List<WeeklyMaterialWeekResult> content = weeks.getContent().stream()
			.map(week -> assemble(week, byWeek.getOrDefault(week, List.of()))).toList();
		return new PageImpl<>(content, pageable, weeks.getTotalElements());
	}

	private static WeeklyMaterialWeekResult assemble(LocalDate week, List<WeeklyMaterialRow> rows) {
		Map<WeeklyMaterialType, WeeklyMaterialRow> byType = rows.stream()
			.collect(Collectors.toMap(WeeklyMaterialRow::materialType, Function.identity()));
		return new WeeklyMaterialWeekResult(week,
			file(byType.get(WeeklyMaterialType.SHEPHERD_GUIDE)),
			file(byType.get(WeeklyMaterialType.SUNDAY_SHARING_SHEET)),
			file(byType.get(WeeklyMaterialType.SATURDAY_LEADER_SHARING_SHEET)));
	}

	private static WeeklyMaterialFileResult file(WeeklyMaterialRow row) {
		return row == null ? null : WeeklyMaterialFileResult.from(row);
	}
}
