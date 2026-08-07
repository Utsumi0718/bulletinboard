package com.example.bulletinboard.service;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;
/*
 * 【クラス全体の役割】
 * Spring Securityのログイン認証処理および新規ユーザー登録（アカウント作成）を担当するサービス層（ビジネスロジック）クラスです。
 *
 * 【追記・補足ポイント】
 * - `UserDetailsService` インターフェースを実装することで、Spring Security がログイン時に自動的にこのクラスの `loadUserByUsername` メソッドを呼び出せるようにしています。
 * - 独自の `User` エンティティ（DBのデータ構造）を、Spring Security が理解できる認証用オブジェクト（`UserDetails`）へ変換する「仲介役」を果たします。
 * - パスワード暗号化（`PasswordEncoder`）と DB 保存（`UserRepository`）を組み合わせたユーザー登録処理（`registerUser`）も提供します。
 * - 指定されたユーザーのパスワードを新しいパスワード（暗号化済み）で更新します。
 * * @param username 対象のユーザー名
 * * @return ユーザーが存在し、更新に成功した場合は true、存在しない場合は false
 * * ログインの成功時・失敗時のカウント操作用メソッドと、Spring Security がアカウントのロック状態（accountNonLocked）を判定できるようにする修正を行います。
*/

@Service // Springのサービス層コンポーネントとしてコンテナに登録（@Autowired可能にする）
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository; // DB操作を行うリポジトリ
  private final PasswordEncoder passwordEncoder; // パスワードのハッシュ化（暗号化）を行うコンポーネント

  public static final int MAX_FAILED_ATTEMPTS = 3; // 最大許容失敗回数

  // コンストラクタインジェクション（Springが自動的に必要な依存関係を注入する）
  public CustomUserDetailsService(UserRepository userRepository, PasswordEncoder passwordEncoder){
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /*
   * 【ログイン認証時に自動呼び出しされるメソッド】
   * ユーザー名をもとにDBからユーザー情報を検索し、Spring Security専用のUserDetails型に変換して返します。
   */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // ユーザー名でDBを検索（見つからない場合は UsernameNotFoundException 例外をスロー）
    User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません" + username));

    // Spring Security 向けのUserDetailsを生成
    return org.springframework.security.core.userdetails.User.builder()
           .username(user.getUsername())
           .password(user.getPassword())
           .accountLocked(!user.isAccountNonLocked())//ロック状態を反映
           .roles("USER")
           .build();
    }

  /*
   * 【新規ユーザー登録メソッド】
   * 生のパスワードをハッシュ化し、安全な状態でDBへ保存します。
   */
  public void registerUser(User user){
    //  入力された生のパスワードをハッシュ化（BCrypt等）してセットし直す
    user.setPassword(passwordEncoder.encode(user.getPassword()));

    // ★追記: 新規登録時は初期値として「ロックなし(true)」「失敗回数(0)」を明示的にセットする
    user.setAccountNonLocked(true);
    user.setFailedAttempt(0);

    //  パスワードが安全になった User オブジェクトを DB に保存する
    userRepository.save(user);
  }

  /*
   *ユーザー名が既に登録されているか確認する
  */

   public boolean existsByUsername(String username){
    return userRepository.existsByUsername(username);
   }



  /*
   * パスワードの再設定メソッド
   * パスワード再設定成功時にアカウントロックも解除するように既存の updatePassword を拡張
   */

  @Transactional
  public boolean updatePassword(String username, String rawNewPassword){
    //データベースから該当のユーザーを検索
    Optional<User> userOptional = userRepository.findByUsername(username);

    //ユーザー存在しない場合はfalseを返す
    if(userOptional.isEmpty()){
       return false;
    }

    //ユーザーが存在する場合はパスワードを暗号化してセット
    User user = userOptional.get();
    user.setPassword(passwordEncoder.encode(rawNewPassword));

    //パスワードを再設定したらアカウントロックを解除して、失敗回数もリセット
    user.setAccountNonLocked(true);
    user.setFailedAttempt(0);

    //DBに保存（更新）
    userRepository.save(user);
    return true;
  }

  /**
   * ログイン失敗時の処理（失敗回数＋1し、3回に達したらロック）
  */
 @Transactional
 public void increaseFailedAttempts(User user){
  int newFailAttempts = user.getFailedAttempt() + 1;
  userRepository.updateFailedAttempts(user.getUsername());

  //3回パスワード入力が失敗したら、アカウントロック
  if(newFailAttempts >= MAX_FAILED_ATTEMPTS){
    userRepository.updateAccountNonLocked(user.getUsername(), false);
  }
 }

 /**
  * ログイン成功時の処理（失敗回数をリセット）
  */
 public void resetFailedAttempts(String username){
   userRepository.resetFailedAttempts(username);
 }

/**
   * ユーザー名でユーザー情報を検索する
   */
  public Optional<User> findByUsername(String username) {
      return userRepository.findByUsername(username);
  }



}