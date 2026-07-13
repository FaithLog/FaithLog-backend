package com.faithlog.devotion.infrastructure.adapter;

import com.faithlog.billing.service.port.DevotionChargeReopenPort;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class BillingDevotionChargeReopenAdapter implements DevotionChargeReopenPort {

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;

	public BillingDevotionChargeReopenAdapter(WeeklyDevotionRecordRepository weeklyRecordRepository) {
		this.weeklyRecordRepository = weeklyRecordRepository;
	}

	@Override
	public void reopenWeeklyDevotion(Long campusId, Long userId, Long weeklyRecordId) {
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository.findById(weeklyRecordId)
			.filter(record -> record.campusId().equals(campusId))
			.filter(record -> record.userId().equals(userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_WEEKLY_RECORD_NOT_FOUND));
		weeklyRecord.reopenForResubmission();
	}
}
