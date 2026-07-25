package com.example.bulletinboard.service;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/*
 * 【クラス全体の役割】
 * Spring Securityのログイン認証処理および新規ユーザー登録（アカウント作成）を担当するサービス層（ビジネスロジック）クラスです。
 *
 * 【追記・補足ポイント】
 * - `UserDetailsService` インターフェースを実装することで、Spring Security がログイン時に自動的にこのクラスの `loadUserByUsername` メソッドを呼び出せるようにしています。
 * - 独自の `User` エンティティ（DBのデータ構造）を、Spring Security が理解できる認証用オブジェクト（`UserDetails`）へ変換する「仲介役」を果たします。
 * - パスワード暗号化（`PasswordEncoder`）と DB 保存（`UserRepository`）を組み合わせたユーザー登録処理（`registerUser`）も提供します。
 */

@Service // Springのサービス層コンポーネントとしてコンテナに登録（@Autowired可能にする）
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository; // DB操作を行うリポジトリ
  private final PasswordEncoder passwordEncoder; // パスワードのハッシュ化（暗号化）を行うコンポーネント

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
    // 1. ユーザー名でDBを検索（見つからない場合は UsernameNotFoundException 例外をスロー）
    User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません" + username));

    // 2. 自作の User エンティティを Spring Security 標準の UserDetails (User) オブジェクトに変換して返す
    return new org.springframework.security.core.userdetails.User(
                user.getUsername(), // ユーザー名
                user.getPassword(), // ハッシュ化されたパスワード
                new ArrayList<>() // 権限リスト（今回は役割・権限指定なしのため空のリスト）
        );
  }

  /*
   * 【新規ユーザー登録メソッド】
   * 生のパスワードをハッシュ化し、安全な状態でDBへ保存します。
   */
  public void registerUser(User user){
    // 1. 入力された生のパスワードをハッシュ化（BCrypt等）してセットし直す
    user.setPassword(passwordEncoder.encode(user.getPassword()));
    // 2. パスワードが安全になった User オブジェクトを DB に保存する
    userRepository.save(user);
  }

}