package com.example.bulletinboard.controller.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.ContactSubmissionService;
import com.example.bulletinboard.service.ContactRateLimitService;

@WebMvcTest(ContactApiController.class)
@Import(SecurityConfig.class)
class ContactPrefillApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContactSubmissionService submission;
    @MockitoBean ContactRateLimitService limiter;
    @MockitoBean UserRepository users;
    private User owner(AccountStatus status) {
        var user=new User(); user.setEmail("owner@example.com"); user.setUsername("owner"); user.setPassword("not-public"); user.setAccountStatus(status);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user)); return user;
    }
    @Test void anonymousIs401() throws Exception {
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isUnauthorized());verifyNoInteractions(users,limiter,submission);
    }
    @Test @WithMockUser(username="owner@example.com") void onlyOwnEmailIsReturnedWithoutCaching() throws Exception {
        owner(AccountStatus.ACTIVE);
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().json("{\"email\":\"owner@example.com\"}",true));
        verifyNoInteractions(limiter,submission);
    }
    @Test @WithMockUser(username="owner@example.com") void clientCannotSelectAnotherUsersEmail() throws Exception {
        owner(AccountStatus.ACTIVE);
        mvc.perform(get("/api/contacts/prefill").param("userId","99").param("email","other@example.com"))
                .andExpect(status().isOk()).andExpect(content().json("{\"email\":\"owner@example.com\"}",true));
        verify(users).findByEmail("owner@example.com"); verifyNoMoreInteractions(users);
    }
    @Test @WithMockUser(username="missing@example.com") void missingUserHasSafe500() throws Exception {
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("ログインユーザー情報を取得できませんでした。"));
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names={"FROZEN","WITHDRAWN"})
    @WithMockUser(username="owner@example.com")
    void nonActiveAccountIsRejected(AccountStatus status) throws Exception {
        owner(status); mvc.perform(get("/api/contacts/prefill")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(username="owner@example.com") void dbFailureDoesNotExposeSqlOrEmail() throws Exception {
        when(users.findByEmail("owner@example.com")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT secret owner@example.com password=fake"));
        var result=mvc.perform(get("/api/contacts/prefill")).andExpect(status().isInternalServerError()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).doesNotContain("SELECT","owner@example.com","password=fake");
    }
}
