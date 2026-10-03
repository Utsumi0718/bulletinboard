package com.example.bulletinboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
class BulletinboardApplicationTests {
	@Autowired
	private MockMvc mvc;
	@Autowired
	private RequestMappingHandlerMapping mappings;

	@Test
	void contextLoads() {
	}

	@Test
	void legacyMvcRoutesAreNotRegistered() {
		var paths = mappings.getHandlerMethods().keySet().stream()
				.flatMap(mapping -> mapping.getPathPatternsCondition().getPatterns().stream())
				.map(pattern -> pattern.getPatternString())
				.toList();
		assertThat(paths).contains("/api/admin/users");
		assertThat(paths).noneMatch(path -> path.equals("/admin") || path.startsWith("/admin/"));
		assertThat(paths).doesNotContain("/login", "/register", "/reset-password",
				"/account/withdraw", "/contact", "/posts", "/logout");
		assertThat(paths).contains("/api/auth/register", "/api/auth/me",
				"/api/auth/password-reset/request", "/api/auth/password-reset/confirm",
				"/api/account/withdraw");
	}

	@Test
	void serverSideTemplatesAndTemplateEngineAreAbsent() {
		var loader = getClass().getClassLoader();
		for (String resource : new String[] {"auth/login.html", "auth/register.html",
				"auth/reset-password.html", "layout/layout.html"}) {
			assertThat(loader.getResource("templates/" + resource)).isNull();
		}
		assertThat(ClassUtils.isPresent("org.thymeleaf.TemplateEngine", loader)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/login", "/register", "/reset-password", "/logout", "/contact", "/posts", "/admin"})
	void legacyGetDoesNotRenderOrRedirect(String path) throws Exception {
		mvc.perform(get(path).with(user("removal-check").roles("ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist("Location"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/register", "/reset-password", "/account/withdraw", "/login", "/logout"})
	void legacyPostHasNoHandler(String path) throws Exception {
		mvc.perform(post(path).with(user("removal-check").roles("ADMIN")).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void anonymousLegacyRequestReturnsJsonWithoutCreatingSavedRequest() throws Exception {
		var result = mvc.perform(get("/login"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/login"))
				.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	void legacyPostWithoutCsrfReturnsJsonForbidden() throws Exception {
		mvc.perform(post("/register"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.path").value("/register"));
	}

}
