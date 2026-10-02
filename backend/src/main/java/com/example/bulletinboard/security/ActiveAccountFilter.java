package com.example.bulletinboard.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 既存セッションでもDB上の凍結・退会を認証必須リクエストごとに確認する。 */
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper mapper;

    public ActiveAccountFilter(UserRepository users, ObjectMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicPath = path.equals("/login") || path.equals("/register") ||
                path.equals("/reset-password") || path.equals("/error") ||
                path.startsWith("/css/") || path.startsWith("/js/") ||
                (path.equals("/api/auth/login") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/auth/logout") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/contacts") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/auth/register") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/csrf") && HttpMethod.GET.matches(request.getMethod()));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        // フォームログインで保存された既存セッションだけを再確認する。
        // Securityテスト等のリクエスト単位の一時認証はDB上のセッションではない。
        boolean savedLogin = session != null &&
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null;
        if (!publicPath && savedLogin && auth != null && auth.isAuthenticated() &&
                auth.getDetails() instanceof WebAuthenticationDetails &&
                !(auth instanceof AnonymousAuthenticationToken) &&
                users.findByEmail(auth.getName()).map(user -> user.getAccountStatus() != AccountStatus.ACTIVE)
                        .orElse(true)) {
            SecurityContextHolder.clearContext();
            if (session != null) session.invalidate();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            if (path.equals("/api") || path.startsWith("/api/")) {
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(response.getWriter(), new ErrorResponse(403, "Forbidden",
                        "このリクエストは許可されていません。", request.getRequestURI()));
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
