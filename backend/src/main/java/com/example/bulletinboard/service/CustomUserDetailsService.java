package com.example.bulletinboard.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

/*
 * 【クラス全体の役割】
 * Spring Securityのログイン認証処理と、
 * ユーザー登録・パスワード更新・ログイン失敗回数管理などを担当する
 * Serviceクラスです。
 *
 * UserDetailsServiceを実装することで、
 * Spring Securityからログイン認証時に
 * loadUserByUsername()が呼び出されます。
 *
 * 【主な役割】
 * - emailによるログインユーザー検索
 * - UserからSpring Security用UserDetailsへの変換
 * - 新規ユーザー登録
 * - パスワードのハッシュ化
 * - ユーザー名・メールアドレスの重複確認
 * - パスワード更新
 * - ログイン失敗回数の管理
 * - ログイン失敗によるアカウントロック管理
 *
 * 【設計上のポイント】
 * - 新しい認証仕様ではusernameではなくemailをログインIDとして使用します。
 * - loadUserByUsernameというメソッド名はSpring Securityの仕様上変更できませんが、
 *   引数にはemailが渡される設計とします。
 * - usernameは公開用のユーザー名として引き続き使用します。
 * - accountNonLockedはパスワード入力失敗によるロック状態を管理します。
 * - ACTIVE / FROZEN / WITHDRAWNによるアカウント状態の認証制御は、
 *   feature/auth-account-refactorで実装します。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /*
     * ログイン失敗を許容する最大回数。
     */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    /*
     * 必要なRepositoryとPasswordEncoderを
     * コンストラクタインジェクションします。
     */
    public CustomUserDetailsService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * Spring Securityによるログイン認証時に呼び出されます。
     *
     * メソッド名はloadUserByUsernameですが、
     * 新しい仕様では引数をemailとして扱います。
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new UsernameNotFoundException(
                    "ユーザーが見つかりません。"
                )
            );

        /*
         * DBのUserをSpring Security用の
         * UserDetailsへ変換します。
         *
         * 新しいログインIDはemailなので、
         * Security側のusernameにもemailを設定します。
         */
        return org.springframework.security.core.userdetails.User
            .builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .accountLocked(!user.isAccountNonLocked())
            .authorities(user.getRole())
            .build();
    }

    /*
     * 新規ユーザーを登録します。
     *
     * 生のパスワードをPasswordEncoderでハッシュ化してから
     * DBへ保存します。
     */
    @Transactional
    public void registerUser(User user) {

        user.setPassword(
            passwordEncoder.encode(user.getPassword())
        );

        /*
         * 新規登録時のセキュリティロック初期状態。
         */
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);

        userRepository.save(user);
    }

    /*
     * 指定したユーザー名が
     * すでに登録されているか確認します。
     */
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /*
     * 指定したメールアドレスが
     * すでに登録されているか確認します。
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /*
     * パスワードを更新します。
     *
     * 新しい認証仕様ではemailを基準に
     * 対象ユーザーを検索します。
     *
     * パスワード入力失敗によってロックされている場合は、
     * パスワード更新と同時にロックを解除します。
     */
    @Transactional
    public boolean updatePassword(
            String email,
            String rawNewPassword) {

        Optional<User> userOptional =
            userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();

        user.setPassword(
            passwordEncoder.encode(rawNewPassword)
        );

        /*
         * パスワード入力失敗による自動ロックの場合は解除します。
         */
        if (user.getFailedAttempt() >= MAX_FAILED_ATTEMPTS) {

            user.setAccountNonLocked(true);
            user.setFailedAttempt(0);
        }

        userRepository.save(user);

        return true;
    }

    /*
     * ログイン失敗時に、
     * failedAttemptを1増加させます。
     *
     * 最大失敗回数に達した場合は
     * accountNonLockedをfalseにしてロックします。
     */
    @Transactional
    public void increaseFailedAttempts(User user) {

        int newFailedAttempts =
            user.getFailedAttempt() + 1;

        userRepository.updateFailedAttempts(
            user.getEmail()
        );

        if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {

            userRepository.updateAccountNonLocked(
                user.getEmail(),
                false
            );
        }
    }

    /*
     * ログイン成功時に、
     * ログイン失敗回数を0へ戻します。
     */
    @Transactional
    public void resetFailedAttempts(String email) {

        userRepository.resetFailedAttempts(email);
    }

    /*
     * メールアドレスからUserを取得します。
     *
     * 主に認証関連処理で使用します。
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {

        return userRepository.findByEmail(email);
    }

    /*
     * ユーザー名からUserを取得します。
     *
     * usernameは公開ユーザー名として使用するため、
     * 認証以外の機能で利用することを想定しています。
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {

        return userRepository.findByUsername(username);
    }
}