package com.faithlog.poll.service;

import com.faithlog.poll.service.command.CreatePollTemplateCommand;
import com.faithlog.poll.service.command.UpdatePollTemplateCommand;
import com.faithlog.poll.service.result.PollTemplateResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PollTemplateService {

	private final PollTemplateCommandService commandService;
	private final PollTemplateQueryService queryService;

	public PollTemplateService(
		PollTemplateCommandService commandService,
		PollTemplateQueryService queryService
	) {
		this.commandService = commandService;
		this.queryService = queryService;
	}

	public PollTemplateResult createTemplate(CreatePollTemplateCommand command) {
		return commandService.createTemplate(command);
	}

	public PollTemplateResult updateTemplate(UpdatePollTemplateCommand command) {
		return commandService.updateTemplate(command);
	}

	public PollTemplateResult deactivateTemplate(Long campusId, Long templateId, Long requesterId) {
		return commandService.deactivateTemplate(campusId, templateId, requesterId);
	}

	public List<PollTemplateResult> listTemplates(Long campusId, Long requesterId) {
		return queryService.listTemplates(campusId, requesterId);
	}

	public PollTemplateResult getTemplate(Long campusId, Long templateId, Long requesterId) {
		return queryService.getTemplate(campusId, templateId, requesterId);
	}
}
