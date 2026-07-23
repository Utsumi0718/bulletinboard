package com.example.bulletinboard.repository;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 【クラス全体の役割】
 * PostRepository（データベース操作層）が正しく動作するか検証するテストクラスです。
 * JPAを経由したデータの検索機能と、保存処理の実行後に実際にデータベースへ正しく値が書き込まれたかを
 * JdbcTemplate（直接SQL実行）を用いて二重チェック（厳密検証）します。
 */

@DataJpaTest // JPAに関連するコンポーネントのみを起動し、高速に統合テストを行う設定
@ActiveProfiles("default") // テスト実行時に使用するSpringの設定プロファイルとして"default"を指定
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // デフォルトの埋め込みDB自動置換を無効化し、application.properties等の設定を適用
public class PostRepositoryTest {

  @Autowired
  private PostRepository postRepository; // テスト対象となるPostRepositoryのインスタンスを自動注入

  @Autowired
  private JdbcTemplate jdbcTemplate; // JPAのキャッシュを介さず、直接SQLを実行してDB内の生の値を検証するためのオブジェクト

  @Test // JUnit5のテストメソッドであることを宣言
  @DisplayName("IDによる新規投稿テスト") // テスト実行結果のレポートに表示される分かりやすい説明用名称
  @Sql("PostRepositoryTest.sql") // テストメソッド実行前に事前データ挿入用SQL（PostRepositoryTest.sql）を自動実行
  void test_findId(){
    // 1. テスト対象メソッドの実行：IDが 1L の投稿データを取得
    Optional<Post> post = postRepository.findById(1L);

    // 2. 検証：取得した Optional の中に Post エンティティが存在することを確認
    assertThat(post).isPresent();

    // 3. 検証：取得した Post のタイトルが SQL ファイルで事前挿入した文字列と一致することを確認
    assertThat(post.get().getTitle()).isEqualTo("テストタイトル1");
  }

  @Test // 2つ目のテストメソッドであることを宣言
  @DisplayName("新規投稿の保存とJDBCによる厳密検証") // テスト名称の設定
  void test_saveAndVerifyWithJdbc() {
    // 1. 保存用のテストデータ（新規 Post オブジェクト）を作成
    Post post = new Post();

    // 2. テストデータの各フィールドに値をセット
    post.setTitle("JDBC検証");
    post.setContent("直接SQLで確認します");

    // 3. テスト対象の save メソッドを実行し、DBに保存（IDが自動割り振りされたエンティティが返る）
    Post saved = postRepository.save(post);

    // 4. JPAの一次キャッシュを経由せず、JdbcTemplate を使って直接 SQL で DB のレコードを取得
    Map<String, Object> result = jdbcTemplate.queryForMap(
      "SELECT * FROM posts WHERE id = ?", saved.getId()
    );

    // 5. 検証：SQLで直接取得した DB 内の "title" 列の値が入力値と一致するか確認
    assertThat(result.get("title")).isEqualTo("JDBC検証");

    // 6. 検証：SQLで直接取得した DB 内の "content" 列の値が入力値と一致するか確認
    assertThat(result.get("content")).isEqualTo("直接SQLで確認します");
  }

}