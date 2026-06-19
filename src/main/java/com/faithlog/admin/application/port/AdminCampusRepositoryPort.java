package com.faithlog.admin.application.port;

import com.faithlog.admin.application.AdminCampusSearchCriteria;
import com.faithlog.campus.domain.Campus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCampusRepositoryPort {

	Page<Campus> searchAdminCampuses(AdminCampusSearchCriteria criteria, Pageable pageable);
}
