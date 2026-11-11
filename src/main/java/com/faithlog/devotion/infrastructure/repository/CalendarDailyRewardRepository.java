package com.faithlog.devotion.infrastructure.repository;

import com.faithlog.devotion.domain.entity.CalendarDailyReward;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarDailyRewardRepository extends JpaRepository<CalendarDailyReward,Long> {
 List<CalendarDailyReward> findByCampusIdAndUserIdAndRecordDate(Long campusId, Long userId, LocalDate date);
 @Query("select coalesce(sum(r.points),0) from CalendarDailyReward r where r.campusId=:campusId and r.userId=:userId")
 long totalAwarded(@Param("campusId") Long campusId,@Param("userId") Long userId);
}
