package com.example.bulletinboard.security;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;


import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.dto.auth.AuthenticatedUserResponse;
import com.example.bulletinboard.dto.auth.LoginFailureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;

/**
 * 【クラス全体の役割】
 * アプリケーション全体のアクセス制御・ログイン・ログアウト処理と、
 * パスワードのハッシュ化方式を定義するSpring Securityの設定クラスです。
 *
 * 【主な役割】
 * - URLごとのアクセス権限を設定する
 * - email + passwordによるAPIログインを設定する
 * - ログイン失敗理由に応じてエラー種別を判定する
 * - ログイン失敗回数によるセキュリティロックを判定する
 * - FROZEN / WITHDRAWNなどのアカウント状態によるログイン拒否を判定する
 * - 一般ユーザーのみ退会処理を実行可能とし、管理者の自己退会を禁止する
 * - 管理者専用URLへのアクセスをROLE_ADMINに限定する
 * - ログアウト時のセッション破棄・Cookie削除を設定する
 * - BCryptを利用したPasswordEncoderをBeanとして登録する
 *
 * 【認証上の設計】
 * - emailをログインIDとして使用します。
 * - accountNonLocked=falseの場合は、ログイン失敗回数による
 *   セキュリティロックとして扱います。
 * - accountStatus=FROZENの場合は、管理者による凍結状態として扱います。
 * - accountStatus=WITHDRAWNの場合は、退会済み状態として扱います。
 * - accountNonLockedとaccountStatusは異なる意味を持つため、
 *   ログイン失敗時のエラーも分けて扱います。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Webアクセス時の認証・認可ルールを定義します。
     *
     * UserRepositoryは、ログイン拒否時に対象ユーザーの
     * accountStatusを確認するために使用します。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            SessionRegistry sessionRegistry) throws Exception {

        // 認証はAPIで提供するため、Security標準のログイン・ログアウトHTMLを生成しない。
        http.removeConfigurer(DefaultLoginPageConfigurer.class);

        http
            .requestCache(cache -> cache.disable())

            // 1. URLごとのアクセス権限（認可）を設定
            .authorizeHttpRequests(auth -> auth

                /*
                 * 未ログインでも利用するAPIとエラー処理のアクセスを許可します。
                 */

                 /*
                  * お問い合わせの新規受付は、
                  * 未ログイン・ログイン済みのどちらでも利用可能にします。
                  *
                  * 許可するのはPOST /api/contactsのみです。
                  * CSRF保護は引き続き適用されます。
                  */
                  .requestMatchers(HttpMethod.POST, "/api/contacts")
                  .permitAll()

                  // CSRFトークンは未ログインでも取得可能
                  .requestMatchers(HttpMethod.GET, "/api/csrf")
                  .permitAll()

                  // 新規登録APIは未ログインで利用し、CSRF保護は維持する
                  .requestMatchers(HttpMethod.POST, "/api/auth/register")
                  .permitAll()

                  .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout")
                  .permitAll()

                  .requestMatchers(HttpMethod.GET, "/api/auth/me")
                  .permitAll()

                  .requestMatchers(HttpMethod.POST,
                          "/api/auth/password-reset/request",
                          "/api/auth/password-reset/confirm")
                  .permitAll()

                  // 管理APIは管理者のみ利用可能
                  .requestMatchers("/api/admin/**")
                  .hasRole("ADMIN")

                  .requestMatchers("/error").permitAll()

                /*
                 * 退会処理は一般ユーザーのみ実行可能とします。
                 *
                 * ROLE_ADMINは責任者アカウントとして扱うため、
                 * 管理者自身による退会処理は許可しません。
                 *
                 * hasRole("USER")はSpring Security内部で
                 * ROLE_USER権限を確認します。
                 */
                .requestMatchers(HttpMethod.POST, "/api/account/withdraw")
                .hasRole("USER")

                /*
                 * 上記以外のURLは、
                 * ログイン済みユーザーのみアクセス可能とします。
                 */
                .anyRequest()
                .authenticated()
            )

            // 2. Spring Securityを利用したAPIログインの設定
            .formLogin(login -> login

                .loginProcessingUrl("/api/auth/login")

                /*
                 * ログインIDとしてusernameではなくemailを使用します。
                 *
                 * POSTパラメータemailと対応します。
                 */
                .usernameParameter("email")

                .successHandler((request, response, authentication) -> {
                    response.setStatus(HttpStatus.OK.value());
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setHeader("Cache-Control", "no-store");
                    objectMapper.writeValue(response.getWriter(),
                            new AuthenticatedUserResponse(true, null, null, null, null));
                })

                /*
                 * ログイン失敗時の処理です。
                 *
                 * エラー原因を判定し、React側が表示を選べる固定JSONを返します。
                 *
                 * 主なエラー種別：
                 *
                 * WRONG_CREDENTIALS
                 *   メールアドレスまたはパスワードが正しくない
                 *
                 * LOCKED
                 *   ログイン失敗回数が上限に達し、
                 *   accountNonLocked=falseになっている
                 *
                 * FROZEN
                 *   管理者によってアカウントが凍結されている
                 *
                 * WITHDRAWN
                 *   退会済みアカウントである
                 *
                 * EMAIL_REQUIRED
                 *   メールアドレスが未入力
                 *
                 * PASSWORD_REQUIRED
                 *   パスワードが未入力
                 *
                 * CREDENTIALS_REQUIRED
                 *   メールアドレス・パスワードの両方が未入力
                 */
                .failureHandler((request, response, exception) -> {

                    // 通常の認証失敗はwrongとして扱う
                    String errorType = "wrong";

                    String emailParam =
                            request.getParameter("email");

                    String passwordParam =
                            request.getParameter("password");

                    boolean isEmailEmpty =
                            emailParam == null
                            || emailParam.trim().isEmpty();

                    boolean isPasswordEmpty =
                            passwordParam == null
                            || passwordParam.trim().isEmpty();

                    /*
                     * ログイン失敗回数によって
                     * accountNonLocked=falseになっている場合。
                     */
                    if (exception instanceof LockedException) {

                        errorType = "locked";

                    /*
                     * accountStatusがACTIVE以外の場合、
                     * Spring SecurityからDisabledExceptionが発生します。
                     *
                     * FROZENとWITHDRAWNではユーザーへ表示する内容が異なるため、
                     * emailからUserを取得し、accountStatusを確認します。
                     */
                    } else if (exception instanceof DisabledException) {

                        if (!isEmailEmpty) {

                            errorType =
                                userRepository
                                    .findByEmail(emailParam)
                                    .map(user -> {

                                        // 管理者によって凍結されている場合
                                        if (user.getAccountStatus()
                                                == AccountStatus.FROZEN) {

                                            return "frozen";
                                        }

                                        // 退会済みの場合
                                        if (user.getAccountStatus()
                                                == AccountStatus.WITHDRAWN) {

                                            return "withdrawn";
                                        }

                                        /*
                                         * ACTIVEなのにDisabledExceptionとなった場合や、
                                         * 想定外の状態の場合は詳細を公開せず、
                                         * 通常の認証失敗として扱います。
                                         */
                                        return "wrong";
                                    })
                                    .orElse("wrong");
                        }

                    } else {

                        /*
                         * ロック・凍結・退会以外の認証失敗では、
                         * フォームの未入力状態を確認します。
                         */
                        if (isEmailEmpty && isPasswordEmpty) {

                            // email・passwordの両方が未入力
                            errorType = "both_empty";

                        } else if (isEmailEmpty) {

                            // emailのみ未入力
                            errorType = "email_empty";

                        } else if (isPasswordEmpty) {

                            // passwordのみ未入力
                            errorType = "password_empty";
                        }
                    }

                    String reason = switch (errorType) {
                        case "locked" -> "LOCKED";
                        case "frozen" -> "FROZEN";
                        case "withdrawn" -> "WITHDRAWN";
                        case "email_empty" -> "EMAIL_REQUIRED";
                        case "password_empty" -> "PASSWORD_REQUIRED";
                        case "both_empty" -> "CREDENTIALS_REQUIRED";
                        default -> "WRONG_CREDENTIALS";
                    };
                    String message = switch (reason) {
                        case "LOCKED" -> "ログインに複数回失敗したため、アカウントがロックされています。";
                        case "FROZEN" -> "このアカウントは凍結されています。";
                        case "WITHDRAWN" -> "このアカウントは退会済みです。";
                        case "EMAIL_REQUIRED" -> "メールアドレスを入力してください。";
                        case "PASSWORD_REQUIRED" -> "パスワードを入力してください。";
                        case "CREDENTIALS_REQUIRED" -> "メールアドレスとパスワードを入力してください。";
                        default -> "メールアドレスまたはパスワードが正しくありません。";
                    };
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setHeader("Cache-Control", "no-store");
                    objectMapper.writeValue(response.getWriter(), new LoginFailureResponse(
                            HttpStatus.UNAUTHORIZED.value(),
                            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                            message,
                            request.getRequestURI(),
                            reason));
                })

            )

           /*
           * Securityで拒否されたリクエストの応答を設定します。
           *
           * - 未ログインによる認証拒否は401の共通JSON
           * - 権限不足・CSRF拒否は403の共通JSON
           */
 
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint( (request, response, authException) -> {              
                
                      ErrorResponse errorResponse = new ErrorResponse(
                      HttpStatus.UNAUTHORIZED.value(),
                      HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                      "ログインが必要です。",
                       request.getRequestURI()
                    );

                     response.setStatus(HttpStatus.UNAUTHORIZED.value());
                     response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                     response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    objectMapper.writeValue(
                        response.getWriter(),
                        errorResponse
                    );
            
             })

            
          // 403：権限不足・CSRFによる拒否
         .accessDeniedHandler((request, response, accessDeniedException) -> {

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.FORBIDDEN.value(),
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    "このリクエストは許可されていません。",
                    request.getRequestURI()
            );

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(
                    response.getWriter(),
                    errorResponse
            );

        })
            )

            .sessionManagement(session -> {
                // Spring Security標準のSession ID変更を明示する。
                session.sessionFixation(fixation -> fixation.changeSessionId());
                session.maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> {
                            event.getResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
                            event.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
                            event.getResponse().setContentType(MediaType.APPLICATION_JSON_VALUE);
                            event.getResponse().setHeader("Cache-Control", "no-store");
                            objectMapper.writeValue(event.getResponse().getWriter(), new ErrorResponse(
                                    401, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                    "ログインが必要です。", event.getRequest().getRequestURI()));
                        });
            })

            // 3. ログアウト処理
            .logout(logout -> logout

                .logoutUrl("/api/auth/logout")

                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                    response.setHeader("Cache-Control", "no-store");
                })

                // サーバー側のセッションを無効化
                .invalidateHttpSession(true)

                // ブラウザ側のセッションCookieを削除
                .deleteCookies("JSESSIONID")

            );

        http.addFilterBefore(new ActiveAccountFilter(userRepository, objectMapper), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * パスワードのハッシュ化に使用するPasswordEncoderを登録します。
     *
     * BCryptを使用し、登録時・ログイン認証時の
     * パスワード照合に利用します。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public static ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }
}
