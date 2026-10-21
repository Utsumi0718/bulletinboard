package com.example.bulletinboard.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        boolean publicPath = path.equals("/error") ||
                (path.equals("/api/auth/login") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/auth/logout") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.startsWith("/api/auth/password-reset/") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/contacts") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/auth/register") && HttpMethod.POST.matches(request.getMethod())) ||
                (path.equals("/api/csrf") && HttpMethod.GET.matches(request.getMethod()));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        // 旧Principalはメール検索へフォールバックせず、公開APIでも認証状態を破棄する。
        boolean legacy = authenticated && !(auth.getPrincipal() instanceof AccountPrincipal);
        boolean inactive = false;
        if (authenticated && !legacy && !publicPath) {
            AccountPrincipal principal = (AccountPrincipal) auth.getPrincipal();
            try {
                inactive = users.findById(principal.userId())
                        .map(user -> user.getAccountStatus() != AccountStatus.ACTIVE
                                || user.getAuthenticationVersion()!=principal.authenticationVersion()).orElse(true);
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) {
                // FilterのDB例外はControllerAdviceへ届かないため、内部情報を含めず応答する。
                response.setStatus(500);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getWriter(), new ErrorResponse(500,"Internal Server Error",
                        "処理に失敗しました。時間をおいて再度お試しください。",request.getRequestURI()));
                return;
            }
        }
        if (legacy || inactive) {
            SecurityContextHolder.clearContext();
            if (session != null) session.invalidate();
            if (publicPath) { chain.doFilter(request, response); return; }
            response.setStatus(legacy ? 401 : 403);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), new ErrorResponse(legacy ? 401 : 403,
                    legacy ? "Unauthorized" : "Forbidden",
                    legacy ? "ログインが必要です。" : "このリクエストは許可されていません。", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
