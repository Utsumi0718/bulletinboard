package com.example.bulletinboard.security;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.exception.ContactRateLimitException;
import com.example.bulletinboard.service.ContactRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/** Securityチェーン内のCSRF検証後・JSON検証前にだけ適用する。 */
public class ContactRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ContactRateLimitFilter.class);
    private final ObjectProvider<ContactRateLimitService> limiter;
    private final ObjectMapper mapper;
    public ContactRateLimitFilter(ObjectProvider<ContactRateLimitService> limiter, ObjectMapper mapper) {
        this.limiter = limiter; this.mapper = mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/contacts".equals(request.getServletPath().isEmpty()
                        ? request.getRequestURI().substring(request.getContextPath().length()) : request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // Forwarded/X-Forwarded-Forを参照しない。本番では信頼プロキシによるremoteAddr設定を別途確認する。
            limiter.getObject().check(request.getRemoteAddr());
        } catch (ContactRateLimitException ex) {
            write(response, request, 429, "Too Many Requests", ex.getMessage()); return;
        } catch (RuntimeException ex) {
            log.error("contact_rate_limit_error errorType={}", ex.getClass().getSimpleName());
            write(response, request, 500, "Internal Server Error", "お問い合わせを受け付けられませんでした。時間をおいて再度お試しください。"); return;
        }
        chain.doFilter(request, response);
    }
    private void write(HttpServletResponse response, HttpServletRequest request, int status, String error, String message) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new ErrorResponse(status, error, message, request.getRequestURI()));
    }
}
