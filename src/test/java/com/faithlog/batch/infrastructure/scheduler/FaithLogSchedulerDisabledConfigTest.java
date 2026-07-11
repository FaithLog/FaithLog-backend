package com.faithlog.batch.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "faithlog.scheduler.enabled=false")
@ActiveProfiles("test")
class FaithLogSchedulerDisabledConfigTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void schedulerBeansAreAbsentWhenSchedulerIsDisabled() {
		assertThat(applicationContext.getBeansOfType(FaithLogScheduledJobs.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(FaithLogSchedulerConfig.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
	}
}
