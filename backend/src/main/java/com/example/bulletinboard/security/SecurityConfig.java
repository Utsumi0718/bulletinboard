package com.example.bulletinboard.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;

/**
 * 【クラス全体の役割】
 * アプリケーション全体のアクセス制御・ログイン・ログアウト処理と、
 * パスワードのハッシュ化方式を定義するSpring Securityの設定クラスです。
 *
 * 【主な役割】
 * - URLごとのアクセス権限を設定する
 * - email + passwordによるフォームログインを設定する
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
            UserRepository userRepository) throws Exception {

        http

            // 1. URLごとのアクセス権限（認可）を設定
            .authorizeHttpRequests(auth -> auth

                /*
                 * お題一覧・ログイン・新規登録・パスワード再設定・
                 * 静的リソースなどは、未ログインユーザーにも公開します。
                 */
                .requestMatchers(
                    "/posts",
                    "/login",
                    "/register",
                    "/reset-password",
                    "/css/**",
                    "/js/**",
                    "/error"
                ).permitAll()

                /*
                 * 退会処理は一般ユーザーのみ実行可能とします。
                 *
                 * ROLE_ADMINは責任者アカウントとして扱うため、
                 * 管理者自身による退会処理は許可しません。
                 *
                 * hasRole("USER")はSpring Security内部で
                 * ROLE_USER権限を確認します。
                 */
                .requestMatchers("/account/withdraw")
                .hasRole("USER")

                /*
                 * 管理者専用URLは、
                 * ROLE_ADMINを持つユーザーのみアクセス可能とします。
                 */
                .requestMatchers("/admin/**")
                .hasRole("ADMIN")

                /*
                 * 上記以外のURLは、
                 * ログイン済みユーザーのみアクセス可能とします。
                 */
                .anyRequest()
                .authenticated()
            )

            // 2. フォームログインの設定
            .formLogin(login -> login

                /*
                 * Spring Security標準のログイン画面ではなく、
                 * アプリケーション独自のログイン画面を使用します。
                 */
                .loginPage("/login")

                /*
                 * ログインIDとしてusernameではなくemailを使用します。
                 *
                 * login.html側の
                 * <input name="email">
                 * と対応します。
                 */
                .usernameParameter("email")

                /*
                 * ログイン成功後はお題一覧画面へ遷移します。
                 */
                .defaultSuccessUrl("/posts", true)

                /*
                 * ログイン失敗時の処理です。
                 *
                 * エラー原因を判定し、
                 * /login?error=xxx の形式でログイン画面へ返します。
                 *
                 * 主なエラー種別：
                 *
                 * wrong
                 *   メールアドレスまたはパスワードが正しくない
                 *
                 * locked
                 *   ログイン失敗回数が上限に達し、
                 *   accountNonLocked=falseになっている
                 *
                 * frozen
                 *   管理者によってアカウントが凍結されている
                 *
                 * withdrawn
                 *   退会済みアカウントである
                 *
                 * email_empty
                 *   メールアドレスが未入力
                 *
                 * password_empty
                 *   パスワードが未入力
                 *
                 * both_empty
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

                    /*
                     * 判定したエラー種別をクエリパラメータとして付与し、
                     * ログイン画面へリダイレクトします。
                     */
                    response.sendRedirect(
                        "/login?error=" + errorType
                    );
                })

                // ログイン処理自体は未認証ユーザーにも許可
                .permitAll()
            )

            /*
             * 未ログイン状態で認証必須ページへ
             * アクセスした場合の処理。
             */
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