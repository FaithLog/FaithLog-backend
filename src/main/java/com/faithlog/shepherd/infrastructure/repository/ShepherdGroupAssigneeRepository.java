package com.faithlog.shepherd.infrastructure.repository;

import com.faithlog.shepherd.domain.entity.ShepherdGroupAssignee;
import com.faithlog.shepherd.domain.entity.ShepherdGroupAssigneeId;
import com.faithlog.shepherd.service.result.ShepherdGroupAssigneeRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShepherdGroupAssigneeRepository extends JpaRepository<ShepherdGroupAssignee, ShepherdGroupAssigneeId> {

	boolean existsByCampusIdAndShepherdGroupIdAndUserId(Long campusId, Long shepherdGroupId, Long userId);

	List<ShepherdGroupAssignee> findByCampusIdAndShepherdGroupIdOrderByUserIdAsc(Long campusId, Long shepherdGroupId);

	void deleteByCampusIdAndShepherdGroupIdAndUserIdIn(Long campusId, Long shepherdGroupId, Collection<Long> userIds);

	@Query("""
		select new com.faithlog.shepherd.service.result.ShepherdGroupAssigneeRow(
			assignee.shepherdGroupId,
			user.id,
			user.name,
			user.email
		)
		from ShepherdGroupAssignee assignee
		join User user on user.id = assignee.userId
		where assignee.shepherdGroupId in :groupIds
		order by assignee.shepherdGroupId asc, user.id asc
		""")
	List<ShepherdGroupAssigneeRow> findAssigneeRowsByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
