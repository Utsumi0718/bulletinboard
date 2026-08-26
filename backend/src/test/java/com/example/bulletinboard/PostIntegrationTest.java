package com.example.bulletinboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach; // ★追加
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder; // ★追加
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User; // ★追加
import com.example.bulletinboard.repository.PostRepository;
import com.example.bulletinboard.repository.UserRepository; // ★追加
import com.example.bulletinboard.security.SecurityConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SecurityConfig.class)
@ActiveProfiles("default")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository; // ★追加：DB操作用

    @Autowired
    private PasswordEncoder passwordEncoder; // ★追加：パスワード暗号化用

    private User testUser;

    // 各テスト実行前にモックユーザー「user」をDBに作成・登録しておく
    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("user").isEmpty()) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("password"));
            testUser = userRepository.save(user);
        } else {
            testUser = userRepository.findByUsername("user").get();
        }
    }

    @Test
    @DisplayName("新規登録したユーザーがログインできるか検証")
    void test_loginFlow() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username", "tester01")
                .param("password", "password123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?register_success"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "tester01")
                .param("password", "password123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));
    }

    @Test
    @DisplayName("間違ったパスワードでログインしてエラーが出るか検証")
    void test_loginFailure() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username", "tester01")
                .param("password", "password345"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?register_success"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "tester01")
                .param("password", "wrongpassword"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=wrong"));
    }

    @Test
    @DisplayName("ユーザー名やパスワードが英数混在でない場合、登録画面に戻りエラーになること")
    void test_registerValidationError_notAlphaNumeric() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("username", "1111111")
                .param("password", "11111111"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("registerForm", "username"))
                .andExpect(model().attributeHasFieldErrors("registerForm", "password"));
    }

    @Test
    @DisplayName("未ログイン状態でのアクセス制御")
    void test_unauthenticatedAccess() throws Exception {
        mockMvc.perform(post("/new")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/posts?error=unauthorized"));
    }

    @Test
    @DisplayName("投稿画面が正常に表示されること")
    @WithMockUser
    void test_listPage() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/list"))
                .andExpect(content().string(containsString("投稿一覧")));
    }

    @Test
    @DisplayName("新規投稿がDBに保存され、一覧へリダイレクトされること")
    @WithMockUser
    void test_createPostFlow() throws Exception {
        mockMvc.perform(post("/posts")
                .with(csrf())
                .param("title", "統合テスト")
                .param("content", "全体を通したテストです"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));
    }

    @Test
    @DisplayName("特定の詳細画面が表示されること")
    @Sql("repository/PostRepositoryTest.sql")
    @WithMockUser
    void test_detailPage() throws Exception {
        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/detail"))
                .andExpect(content().string(containsString("テストタイトル1")));
    }

    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @DisplayName("ログイン済みのユーザなら投稿したものが、削除されるか検証(PRG対応版)")
    @WithMockUser
    void test_deletePostFlow() throws Exception {
        // SQLで読み込んだ投稿(ID:1)にテストユーザーを紐づけて本人扱いにする
        Post post = postRepository.findById(1L).orElseThrow();
        post.setUser(testUser);
        postRepository.save(post);

        mockMvc.perform(post("/posts/1/delete").with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));

        Optional<Post> deletedPost = postRepository.findById(1L);
        assertThat(deletedPost).isEmpty();
    }


    @Test
    @WithMockUser
    @DisplayName("タイトルと投稿内容が空だと、バリデーションエラーが発生すること")
    void test_createValidationOverUp() throws Exception {
        mockMvc.perform(post("/posts")
                .param("title", "")
                .param("content", "")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/new"))
                .andExpect(model().attributeHasFieldErrors("post", "title"))
                .andExpect(model().attributeHasFieldErrors("post", "content"));
    }

    @Test
    @WithMockUser(username = "user")
    @DisplayName("ログイン済みのユーザーなら新規投稿でき、投稿とユーザーが自動紐づけされること")
    void test_createPostWithUser() throws Exception {
        mockMvc.perform(post("/posts")
                .with(csrf())
                .param("title", "セキュリティテスト")
                .param("content", "テスト内容"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));

        // データベースに保存された投稿とユーザーの紐づけを検証
        List<Post> posts = postRepository.findAll();
        Post createdPost = posts.stream()
                .filter(p -> "セキュリティテスト".equals(p.getTitle()))
                .findFirst()
                .orElseThrow();

        assertThat(createdPost.getUser()).isNotNull();
        assertThat(createdPost.getUser().getUsername()).isEqualTo("user");
    }

    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @WithMockUser
    @DisplayName("ログイン済みのユーザなら編集画面が表示されるか検証")
    void test_editPage() throws Exception {
        // 本人チェックを通過させるために投稿へユーザーを紐づけ
        Post post = postRepository.findById(1L).orElseThrow();
        post.setUser(testUser);
        postRepository.save(post);

        mockMvc.perform(get("/posts/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/edit"));
    }

    @Test
    @Sql("repository/PostRepositoryTest.sql")
    @WithMockUser
    @DisplayName("投稿したタイトルや内容が編集（更新）されるか検証")
    void test_editPostFlow() throws Exception {
        // 本人チェックを通過させるために投稿へユーザーを紐づけ
        Post post = postRepository.findById(1L).orElseThrow();
        post.setUser(testUser);
        postRepository.save(post);

        mockMvc.perform(post("/posts/1")
                .with(csrf())
                .param("title", "更新：タイトル")
                .param("content", "更新：内容"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));

        Optional<Post> updatedPostOpt = postRepository.findById(1L);
        assertThat(updatedPostOpt).isPresent();

        Post updatedPost = updatedPostOpt.get();
        assertThat(updatedPost.getTitle()).isEqualTo("更新：タイトル");
        assertThat(updatedPost.getContent()).isEqualTo("更新：内容");
    }

    @Test
    @WithMockUser
    @DisplayName("ログアウトの検証")
    void test_logoutFlow() throws Exception {
        mockMvc.perform(post("/logout")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));
    }
}