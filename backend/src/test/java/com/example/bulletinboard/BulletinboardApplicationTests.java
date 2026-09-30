package com.example.bulletinboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class BulletinboardApplicationTests {
	@Autowired
	private RequestMappingHandlerMapping mappings;

	@Test
	void contextLoads() {
	}

	@Test
	void legacyAdminRoutesAreNotRegistered() {
		var paths = mappings.getHandlerMethods().keySet().stream()
				.flatMap(mapping -> mapping.getPathPatternsCondition().getPatterns().stream())
				.map(pattern -> pattern.getPatternString())
				.toList();
		assertThat(paths).contains("/api/admin/users");
		assertThat(paths).noneMatch(path -> path.equals("/admin") || path.startsWith("/admin/"));
	}

}
