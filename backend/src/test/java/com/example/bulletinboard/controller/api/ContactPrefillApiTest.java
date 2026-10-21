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
import com.example.bulletinboard.support.WithAccount;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.ContactSubmissionService;
import com.example.bulletinboard.service.ContactRateLimitService;

@WebMvcTest(ContactApiController.class)
@Import(SecurityConfig.class)
@org.junit.jupiter.api.extension.ExtendWith(com.example.bulletinboard.support.TestActorExtension.class)
class ContactPrefillApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContactSubmissionService submission;
    @MockitoBean ContactRateLimitService limiter;
    @MockitoBean UserRepository users;
    private User owner(AccountStatus status) {
        var user=new User(); user.setEmail("owner@example.com"); user.setUsername("owner"); user.setPassword("not-public"); user.setAccountStatus(status);
        user.setId(com.example.bulletinboard.support.TestActors.id("owner@example.com"));
        when(users.findById(user.getId())).thenReturn(Optional.of(user)); return user;
    }
    @Test void anonymousIs401() throws Exception {
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isUnauthorized());verifyNoInteractions(users,limiter,submission);
    }
    @Test @WithAccount(username="owner@example.com") void onlyOwnEmailIsReturnedWithoutCaching() throws Exception {
        owner(AccountStatus.ACTIVE);
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().json("{\"email\":\"owner@example.com\"}",true));
        verifyNoInteractions(limiter,submission);
    }
    @Test @WithAccount(username="owner@example.com") void clientCannotSelectAnotherUsersEmail() throws Exception {
        owner(AccountStatus.ACTIVE);
        mvc.perform(get("/api/contacts/prefill").param("userId","99").param("email","other@example.com"))
                .andExpect(status().isOk()).andExpect(content().json("{\"email\":\"owner@example.com\"}",true));
        verify(users,times(2)).findById(com.example.bulletinboard.support.TestActors.id("owner@example.com")); verifyNoMoreInteractions(users);
    }
    @Test @WithAccount(username="missing@example.com") void missingUserHasSafe500() throws Exception {
        // 認証チェック直後に利用者が取得不能になった場合をControllerで検証する。
        when(users.findById(com.example.bulletinboard.support.TestActors.id("missing@example.com")))
                .thenReturn(Optional.of(new User()), Optional.empty());
        mvc.perform(get("/api/contacts/prefill")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("ログインユーザー情報を取得できませんでした。"));
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names={"FROZEN","WITHDRAWN"})
    @WithAccount(username="owner@example.com")
    void nonActiveAccountIsRejected(AccountStatus status) throws Exception {
        owner(status); mvc.perform(get("/api/contacts/prefill")).andExpect(status().isForbidden());
    }
    @Test @WithAccount(username="owner@example.com") void dbFailureDoesNotExposeSqlOrEmail() throws Exception {
        User active=owner(AccountStatus.ACTIVE);
        when(users.findById(active.getId())).thenReturn(Optional.of(active))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT secret owner@example.com password=fake"));
        var result=mvc.perform(get("/api/contacts/prefill")).andExpect(status().isInternalServerError()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).doesNotContain("SELECT","owner@example.com","password=fake");
    }
}
