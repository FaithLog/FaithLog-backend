package com.faithlog.weeklymaterial.service.port;

import java.util.List;

public interface WeeklyMaterialRecipientPort {
	List<WeeklyMaterialRecipient> findAllActiveRecipients();
}
