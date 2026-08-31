package com.example.bulletinboard.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * 【クラス全体の役割】
 * アプリケーション全体のアクセス制御・ログイン・ログアウト処理と、
 * パスワードのハッシュ化方式を定義するSpring Securityの設定クラスです。
 *
 * 【主な役割】
 * - URLごとのアクセス権限を設定する
 * - email + passwordによるフォームログインを設定する
 * - ログイン失敗時のエラー内容に応じて遷移先を制御する
 * - 管理者専用URLへのアクセスをROLE_ADMINに限定する
 * - ログアウト時のセッション破棄・Cookie削除を設定する
 * - BCryptを利用したPasswordEncoderをBeanとして登録する
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Webアクセス時の認証・認可ルールを定義します。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // 1. URLごとのアクセス権限（認可）を設定
            .authorizeHttpRequests(auth -> auth

                // お題一覧・ログイン・新規登録・パスワード再設定・
                // 静的リソースなどは未ログインユーザーにも公開
                .requestMatchers(
                    "/posts",
                    "/login",
                    "/register",
                    "/reset-password",
                    "/css/**",
                    "/js/**",
                    "/error"
                ).permitAll()

                // 管理者専用URLはROLE_ADMINを持つユーザーのみアクセス可能
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // 上記以外のURLはログイン済みユーザーのみアクセス可能
                .anyRequest().authenticated()
            )

            // 2. フォームログインの設定
            .formLogin(login -> login

                // Spring Security標準画面ではなく自作ログイン画面を使用
                .loginPage("/login")

                // ログインIDとしてusernameではなくemailを使用
                // login.html側のinput name="email"と対応する
                .usernameParameter("email")

                // ログイン成功後はお題一覧画面へ遷移
                .defaultSuccessUrl("/posts", true)

                // ログイン失敗時の処理
                .failureHandler((request, response, exception) -> {

                    // 通常の認証失敗はwrongとして扱う
                    String errorType = "wrong";

                    // ログイン失敗回数によってアカウントがロックされている場合
                    if (exception instanceof LockedException) {
                        errorType = "locked";

                    } else {
                        // ロック以外の場合はフォームの未入力状態を確認
                        String emailParam = request.getParameter("email");
                        String passwordParam = request.getParameter("password");

                        boolean isEmailEmpty =
                                emailParam == null || emailParam.trim().isEmpty();

                        boolean isPasswordEmpty =
                                passwordParam == null || passwordParam.trim().isEmpty();

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

                    // 判定したエラー種別をクエリパラメータとしてログイン画面へ返す
                    response.sendRedirect("/login?error=" + errorType);
                })

                // ログイン処理自体は未認証ユーザーにも許可
                .permitAll()
            )

            // 未ログイン状態で認証必須ページへアクセスした場合の処理
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint(
                        "/posts?error=unauthorized"
                    )
                )
            )

            // 3. ログアウト処理
            .logout(logout -> logout

                // POST /logoutでログアウト処理を実行
                .logoutUrl("/logout")

                // ログアウト成功後はログイン画面へ遷移
                .logoutSuccessUrl("/login?logout")

                // サーバー側のセッションを無効化
                .invalidateHttpSession(true)

                // ブラウザ側のセッションCookieを削除
                .deleteCookies("JSESSIONID")

                // ログアウト処理へのアクセスを許可
                .permitAll()
            );

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
}