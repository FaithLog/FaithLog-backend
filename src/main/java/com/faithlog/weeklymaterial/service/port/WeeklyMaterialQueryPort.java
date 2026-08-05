package com.faithlog.weeklymaterial.service.port;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WeeklyMaterialQueryPort {
	List<WeeklyMaterialRow> findActiveRows(List<LocalDate> weekStartDates);
	Page<LocalDate> findActiveWeekDates(LocalDate fromInclusive, LocalDate toExclusive, Pageable pageable);
}
