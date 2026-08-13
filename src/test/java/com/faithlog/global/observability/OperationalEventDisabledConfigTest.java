package com.faithlog.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"faithlog.observability.enabled=false",
	"faithlog.scheduler.enabled=false"
})
@ActiveProfiles("test")
class OperationalEventDisabledConfigTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void disabled_observability_keeps_one_noop_port_so_application_dependencies_start() {
		var ports = applicationContext.getBeansOfType(OperationalEventPort.class);

		assertThat(ports).hasSize(1);
		assertThat(ports.values().iterator().next().getClass().getSimpleName())
			.isEqualTo("NoOpOperationalEventAdapter");
	}
}
