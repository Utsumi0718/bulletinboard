package com.example.bulletinboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.annotation.Import; // ★これが不足しています

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;
import com.example.bulletinboard.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * 【クラス全体の役割】
 * アプリケーション全体の統合テスト（結合テスト）を行うクラスです。
 * コントローラー、サービス、リポジトリ、データベース、画面描画（Thymeleaf）までを実際の動きに近い形で一気通貫で起動し、
 * 「画面が表示されるか」「フォーム送信後に正しいURLへリダイレクトされるか」など、ブラウザを通した一連の流れを擬似的に検証します。
 */

@SpringBootTest // アプリ全体の全機能（設定・Bean）を実際に起動してテストを行うアノテーション
@AutoConfigureMockMvc // 画面操作をシミュレートする「MockMvc」の準備を自動で行う設定
@Transactional // テストメソッドが終わるたびにデータベースの変更をロールバック（元通りにリセット）する
@Import(SecurityConfig.class) // ★追加：セキュリティ設定を明示的に読み込む
@ActiveProfiles("default") // application-default.properties（H2用の設定など）を読み込む
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 設定ファイルで指定したH2データベース設定をそのまま利用する
public class PostIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // 擬似的なブラウザ操作（リクエスト送信・レスポンス検証）を行うためのオブジェクト

    @Autowired
    private PostRepository postRepository; // テスト対象となるPostRepositoryのインスタンスを自動注入

    @Test
    @DisplayName("新規登録したユーザーがログインできるか検証")
    void test_loginFlow() throws Exception{

  // 1. ユーザー登録
     mockMvc.perform(post("/register").with(csrf()) // ★ここに .with(csrf()) を追加！
        .param("username", "tester")
        .param("password", "password123"))
        .andExpect(status().isFound()) // 登録後はログイン画面へリダイレクト
        .andExpect(redirectedUrl("/login?register_success"));


        // 2. 1で登録したアカウントでログイン処理（POST /login）
    mockMvc.perform(post("/login")
            .with(csrf())
            .param("username", "tester")
            .param("password", "password123"))
            .andExpect(status().isFound()) // ログイン成功のリダイレクト
            .andExpect(redirectedUrl("/posts")); // ログイン完了後の画面へ

    }

    @Test
    @DisplayName("間違ったパスワードでログインしてエラーが出るか検証")
    void test_loginFailure() throws Exception{

    // ユーザー登録
     mockMvc.perform(post("/register").with(csrf()) // ★ここに .with(csrf()) を追加！
        .param("username", "tester01")
        .param("password", "password345"))
        .andExpect(status().isFound()) // 登録後はログイン画面へリダイレクト
        .andExpect(redirectedUrl("/login?register_success"));


        // 間違ったパスワードでログイン
    mockMvc.perform(post("/login")
            .with(csrf())
            .param("username", "tester01")
            .param("password", "wrongpassword"))//間違ったパスワードを入力
            .andExpect(status().isFound()) // ログイン失敗時ののリダイレクト
            .andExpect(redirectedUrl("/login?error")); // ログイン失敗時の画面へ

    }

    @Test
    @DisplayName("未ログイン状態でのアクセス制御")
    void test_unauthenticatedAccess() throws Exception{
      mockMvc.perform(post("/new")
              .with(csrf()))
              .andExpect(status().isFound())//リダイレクトを確認
              .andExpect(redirectedUrl("http://localhost/posts?error=unauthorized"));//新規投稿できないことを確認
    }

    @Test // テストメソッドであることを宣言
    @DisplayName("投稿画面が正常に表示されること") // テスト結果に表示されるわかりやすい説明文
     @WithMockUser
    void test_listPage() throws Exception {
        // 1. HTTP GET で "/posts" にアクセス（画面を開く）
        mockMvc.perform(get("/posts"))
        // 2. レスポンスのステータスコードが 200 OK であることを検証
        .andExpect(status().isOk())
        // 3. 返されたHTMLビューの名前が "posts/list"（templates/posts/list.html）であることを検証
        .andExpect(view().name("posts/list"))
        // 4. 生成されたHTMLのテキスト内に「投稿一覧」という文字列が含まれているか検証
        .andExpect(content().string(containsString("投稿一覧")));
    }

   @Test
@DisplayName("新規投稿がDBに保存され、一覧へリダイレクトされること")
@WithMockUser
void test_createPostFlow() throws Exception {
    // 1. HTTP POST で "/posts" へデータ（タイトル・内容）を送信（フォーム投稿の擬似実行）
    mockMvc.perform(post("/posts")
          .with(csrf()) // ★ここを追加：CSRFトークンを付与する
          .param("title", "統合テスト")
          .param("content", "全体を通したテストです"))
          // 2. 送信成功後のレスポンスが 302 Found（リダイレクト指示）であることを検証
          .andExpect(status().isFound())
          // 3. リダイレクト先のURLが "/posts" であることを検証
          .andExpect(redirectedUrl("/posts"));
}


    @Test
    @DisplayName("特定の詳細画面が表示されること")
    @Sql("repository/PostRepositoryTest.sql") // テスト実行前に事前データ挿入SQLを実行する
    @WithMockUser
    void test_detailPage() throws Exception {
        // 1. SQLファイルでID:1のデータが挿入された状態で、GET "/posts/1" にアクセス
        mockMvc.perform(get("/posts/1"))
              // 2. ステータスコードが 200 OK であることを検証
              .andExpect(status().isOk())
              // 3. 表示されるビューが "posts/detail" であることを検証
              .andExpect(view().name("posts/detail"))
              // 4. 画面内にSQLで挿入した「テストタイトル1」が含まれているかを検証
              .andExpect(content().string(containsString("テストタイトル1")));
    }

    // 投稿削除のテスト
    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @DisplayName("ログイン済みのユーザなら投稿したものが、削除されるか検証(PRG対応版)")
    @WithMockUser
    void test_deletePostFlow() throws Exception {
        mockMvc.perform(post("/posts/1/delete").with(csrf())) // ID1が削除そのリクエストを送信する
               .andExpect(status().isFound()) // 302/303 Found(リダイレクト)を確認する
               .andExpect(redirectedUrl("/posts/delete-complete")); // 遷移先のURLを確認

        Optional<Post> post = postRepository.findById(1L); // IDが 1L の投稿データを取得
        assertThat(post).isEmpty(); // 検証：取得した Optional の中に Post エンティティが削除されてることを確認
    }

    @Test
    @DisplayName("削除完了画面が表示されるか検証する")
    @WithMockUser
    void test_deleteCompletePage() throws Exception {
        mockMvc.perform(get("/posts/delete-complete"))
               .andExpect(status().isOk()) // ステータスコードが 200 OK であることを検証
               .andExpect(view().name("posts/deleteComplete")); // 表示されるビューがdeleteCompleteであることを確認
    }

    // (異常系のテスト)
    @Test
    @WithMockUser // ログイン状態（モックユーザー）を擬似作成する
    @DisplayName("タイトルと投稿内容が空だと、バリデーションエラーが発生すること")
    void test_createValidationOverUp() throws Exception {
        mockMvc.perform(post("/posts")
               .param("title", "") // タイトルを空にする
               .param("content", "")//内容を空にする
                .with(csrf())) // ★ここを追加：CSRFトークンを付与する
               .andExpect(status().isOk()) // 画面がリダイレクトされずに200OK
               .andExpect(view().name("posts/new")) // 入力画面に戻っているか
               .andExpect(model().attributeHasFieldErrors("post", "title"))
               .andExpect(model().attributeHasFieldErrors("post", "content"));
    }

    // ログイン済みのユーザーで投稿するテスト
    @Test
    @WithMockUser // ログイン状態（モックユーザー）を擬似作成する
    @DisplayName("ログイン済みのユーザーなら新規投稿できる")
    void test_createPostWithUser() throws Exception {
        mockMvc.perform(post("/posts") // ★修正：先頭にスラッシュを追加 ("/posts")
               .with(csrf()) // CSRFトークンを付与
               .param("title", "セキュリティテスト") // ★修正：下のassertThat検証とタイトルを「セキュリティテスト」に合致させる
               .param("content", "テスト内容"))
               .andExpect(status().isFound()) // リダイレクト確認
               .andExpect(redirectedUrl("/posts")); // リダイレクト確認

        // データベースに保存されていることも念のため確認
        List<Post> posts = postRepository.findAll();
        assertThat(posts).extracting(Post::getTitle).contains("セキュリティテスト");
    }

    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @WithMockUser
    @DisplayName("ログイン済みのユーザなら編集画面が表示されるか検証")
    void test_editPage() throws Exception{
       mockMvc.perform(get("/posts/1/edit"))
              .andExpect(status().isOk())//ステータスコードが200であることを検証
              .andExpect(view().name("posts/edit"));//posts配下のedit.htmlが表示されればOK
    }

    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @WithMockUser
    @DisplayName("投稿したタイトルや内容が編集（更新）されるか検証")
    void test_editPostFlow () throws Exception{
         mockMvc.perform(post("/posts/1")
                .with(csrf())
                .param("title", "更新：タイトル")
                .param("content","更新：内容"))//編集のリクエストを送信
                .andExpect(status().isFound())//更新後は一覧へリダイレクトされているか
                .andExpect(redirectedUrl("/posts"));//リダイレクトの確認

                //編集した内容が反映されているか確認
              Optional<Post> post =  postRepository.findById(1L); // IDが 1L の投稿データを取得
              assertThat(post).isPresent();//中身が入ってるか確認
              ////Optionalの中身を取り出してタイトルと内容が更新されているか検証
              Post updatePost = post.get();
              assertThat(updatePost.getTitle()).isEqualTo("更新：タイトル");
              assertThat(updatePost.getContent()).isEqualTo("更新：内容");

    }

    @Test
    @WithMockUser
    @DisplayName("ログアウトの検証")
    void test_logoutFlow() throws Exception{
      mockMvc.perform(post("/logout")
             .with(csrf()))//ログアウトのリクエストの送信
             .andExpect(status().isFound())//ログアウト後はログイン画面へリダイレクト
             .andExpect(redirectedUrl("/login?logout"));

    }



}
