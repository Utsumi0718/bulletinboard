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

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


import static org.hamcrest.Matchers.containsString;

/*
 * 【クラス全体の役割】
 * アプリケーション全体の統合テスト（結合テスト）を行うクラスです。
 * コントローラー、サービス、リポジトリ、データベース、画面描画（Thymeleaf）までを実際の動きに近い形で一気通貫で起動し、
 * 「画面が表示されるか」「フォーム送信後に正しいURLへリダイレクトされるか」など、ブラウザを通した一連の流れを擬似的に検証します。
 */

@SpringBootTest // アプリ全体の全機能（設定・Bean）を実際に起動してテストを行うアノテーション
@AutoConfigureMockMvc // 画面操作をシミュレートする「MockMvc」の準備を自動で行う設定
@Transactional // テストメソッドが終わるたびにデータベースの変更をロールバック（元通りにリセット）する
@ActiveProfiles("default") // application-default.properties（H2用の設定など）を読み込む
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 設定ファイルで指定したH2データベース設定をそのまま利用する
public class PostIntegrationTest {

   @Autowired
   private MockMvc mockMvc; // 擬似的なブラウザ操作（リクエスト送信・レスポンス検証）を行うためのオブジェクト

  @Autowired
  private PostRepository postRepository; // テスト対象となるPostRepositoryのインスタンスを自動注入



   @Test // テストメソッドであることを宣言
   @DisplayName("投稿画面が正常に表示されること") // テスト結果に表示されるわかりやすい説明文
   void test_listPage () throws Exception {
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
   void test_createPostFlow() throws Exception {
       // 1. HTTP POST で "/posts" へデータ（タイトル・内容）を送信（フォーム投稿の擬似実行）
       mockMvc.perform(post("/posts")
              .param("title", "統合テスト") // フォームの「title」項目に「統合テスト」を入力
              .param("content", "全体を通したテストです")) // フォームの「content」項目に入力
              // 2. 送信成功後のレスポンスが 302 Found（リダイレクト指示）であることを検証
              .andExpect(status().isFound())
              // 3. リダイレクト先のURLが "/posts" であることを検証
              .andExpect(redirectedUrl("/posts"));
   }

   @Test
   @DisplayName("特定の詳細画面が表示されること")
   @Sql("repository/PostRepositoryTest.sql") // テスト実行前に事前データ挿入SQLを実行する
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
   @DisplayName("投稿したものが、削除されるか検証(PRG対応版)")
   void test_deletePostFlow() throws Exception{
    mockMvc.perform(post("/posts/1/delete"))//ID1が削除そのリクエストを送信する
           .andExpect(status().isFound())// 303 Found(リダイレクト)を確認する
           .andExpect(redirectedUrl("/posts/delete-complete")); //遷移先のURLを角印ん

           Optional<Post> post = postRepository.findById(1L);//IDが 1L の投稿データを取得
           assertThat(post).isEmpty();  //検証：取得した Optional の中に Post エンティティが削除されてることを確認
   }

   @Test
   @DisplayName("削除完了画面が表示されるか検証する")
   void test_deleteCompletePage() throws Exception{
      mockMvc.perform(get("/posts/delete-complete"))
             .andExpect(status().isOk())//ステータスコードが 200 OK であることを検証
             .andExpect(view().name("posts/deleteComplete"));//表示されるビューがdeleteCompleteであることを確認
   }


   //(異常系のテスト)
   @Test
   @DisplayName("タイトルと投稿内容が空だと、バリテーションエラーが発生すること")
   void test_createValidationOverUp() throws Exception{
      mockMvc.perform(post("/posts")
             .param("title","")//タイトルを空にする
             .param("content",""))//投稿内容も空にする
             .andExpect(status().isOk())//画面がリダイレクトされずに200OK
             .andExpect(view().name("posts/new"))//入力画面に戻っているか
             .andExpect(model().attributeHasFieldErrors("post","title"))
             .andExpect(model().attributeHasFieldErrors("post","content"));

    }




}