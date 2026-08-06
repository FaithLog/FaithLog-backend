package com.faithlog.weeklymaterial.service.port;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WeeklyMaterialQueryPort {
	List<WeeklyMaterialRow> findActiveRows(Long campusId, List<LocalDate> weekStartDates);
	Page<LocalDate> findActiveWeekDates(Long campusId, LocalDate fromInclusive, LocalDate toExclusive,
		Pageable pageable);
}
