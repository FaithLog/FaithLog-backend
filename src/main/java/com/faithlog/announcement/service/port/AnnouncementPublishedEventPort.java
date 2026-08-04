package com.faithlog.announcement.service.port;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;

public interface AnnouncementPublishedEventPort {

	void recordPublished(Announcement announcement, AnnouncementCategory category);
}
