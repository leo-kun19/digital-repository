package com.example.thesisrepo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.event.ApplicationEventsTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;

@SpringBootTest
@ActiveProfiles("test")
@TestExecutionListeners(
	listeners = {
		ServletTestExecutionListener.class,
		DirtiesContextBeforeModesTestExecutionListener.class,
		ApplicationEventsTestExecutionListener.class,
		DependencyInjectionTestExecutionListener.class,
		DirtiesContextTestExecutionListener.class
	},
	mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ThesisRepoApplicationTests {

	@Test
	void contextLoads() {
	}

}
