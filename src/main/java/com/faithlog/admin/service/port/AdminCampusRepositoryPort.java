package com.faithlog.admin.service.port;

import com.faithlog.admin.service.query.AdminCampusSearchCriteria;
import com.faithlog.campus.domain.Campus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCampusRepositoryPort {

	Page<Campus> searchAdminCampuses(AdminCampusSearchCriteria criteria, Pageable pageable);
}
