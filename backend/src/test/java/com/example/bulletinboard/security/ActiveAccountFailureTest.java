package com.example.bulletinboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.bulletinboard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 認証状態確認に失敗したとき、保護対象へ通さずDBの内部情報も返さない。 */
class ActiveAccountFailureTest {
    static Stream<RuntimeException> failures() {
        String internal="SELECT private_data fictional@example.test secret=fake";
        return Stream.of(new org.springframework.dao.DataAccessResourceFailureException(internal),
                new org.springframework.transaction.TransactionSystemException(internal));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @ParameterizedTest @MethodSource("failures")
    void databaseFailureIsSafeAndDoesNotInvokeProtectedHandler(RuntimeException failure) throws Exception {
        var repository=mock(UserRepository.class);
        when(repository.findById(7L)).thenThrow(failure);
        var principal=new AccountPrincipal(7L,"unused",true,true,List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var request=new MockHttpServletRequest("GET","/api/auth/me");
        var response=new MockHttpServletResponse();
        var chain=mock(jakarta.servlet.FilterChain.class);
        new ActiveAccountFilter(repository,new ObjectMapper()).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).contains("Internal Server Error","/api/auth/me")
                .doesNotContain("SELECT","fictional@example.test","secret=fake");
        verifyNoInteractions(chain);
    }
}
