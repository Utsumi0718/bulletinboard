package com.example.bulletinboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/*
 * 【クラス全体の役割】
 * アプリケーション全体の「アクセス制御（セキュリティ方針）」および「パスワード暗号化方式」を定義する設定クラスです。
 *
 * 【主な役割】
 * - 「誰がどのURL（ページ）にアクセスできるか」という閲覧権限・ログイン必須ルールの定義
 * - ログイン画面・ログアウト処理の振る舞い（遷移先URL、セッション破棄など）の設定
 * - パスワードを安全にハッシュ化（暗号化）するための暗号化アルゴリズム（BCrypt）の準備
 */
@Configuration // このクラスがSpringの設定ファイル（Bean定義）であることを宣言
@EnableWebSecurity // Spring SecurityのWebセキュリティ機能（フィルタチェーンなど）を有効化する
public class SecurityConfig {

    // Webアクセス時のセキュリティルールを定義するメインの設定Bean
    @Bean
    public SecurityFilterChain securityFilterChain (HttpSecurity http) throws Exception {
     http
        // 1. URLごとのアクセス権限（認可）ルールを設定
        .authorizeHttpRequests(auth -> auth
          // ログイン画面、新規登録画面、静的リソース（CSS/JS等）は未ログインでもアクセス許可（全員OK）
          .requestMatchers("/posts","/login", "/register", "/css/**", "/js/**").permitAll()
          // 上記以外のすべてのURL（投稿一覧、作成、詳細、削除など）はログイン状態（認証済み）を必須とする
          .anyRequest().authenticated()
        )
        // 2. フォーム認証（ログイン画面）に関する動作を設定
        .formLogin(login -> login
         .loginPage("/login") // デフォルトのログイン画面ではなく、自作の "/login" 画面を表示に使用する
         .defaultSuccessUrl("/posts", true) // ログイン成功時の移動先URL（/posts：投稿一覧）を設定
         .permitAll() // ログイン画面処理自体へのアクセスは全員に許可する
        )
        // 3. ログアウト処理に関する動作を設定
        .logout(logout -> logout
         .logoutUrl("/logout") // ログアウト処理を実行するためのURLを指定（ここにPOSTするとログアウト）
         .logoutSuccessUrl("/login?logout") // ログアウト成功後に遷移する画面URLを指定（※タイポ修正：login?/logout -> /login?logout）
         .invalidateHttpSession(true) // サーバー側に保存されているユーザーのセッション情報を完全に消去する
         .deleteCookies("JSESSIONID") // ブラウザ側に保存されているセッション識別用のクッキーを削除する
         .permitAll() // ログアウト処理自体へのアクセスは全員に許可する
        );

        // 設定を反映させた SecurityFilterChain オブジェクトをビルドしてSpringに渡す
        return http.build();
   }

   // パスワードのハッシュ化（暗号化）を担当するBeanの登録
   @Bean
   public PasswordEncoder passwordEncoder(){
    // BCryptハッシュアルゴリズム（強力で安全な標準的ハッシュ化方式）を使用する実装を返す
    return new BCryptPasswordEncoder();
   }

}