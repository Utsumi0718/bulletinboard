package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.exception.ContactSaveException;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.ContactSubmissionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;

/**
 * 【クラスの役割】
 * お問い合わせ受付APIの入力検証・HTTP応答・
 * 受付Serviceへの入力値の受け渡しを検証するテストです。
 *
 * 実際のSecurityConfigを読み込み、
 * 匿名受付、CSRF保護、認証・認可エラーの応答も確認します。
 *
 * 【検証内容】
 * ☑ ログイン済み・未ログイン双方の正常受付
 *   → 201 Createdと受付完了メッセージ
 *   → 入力4項目を受付Serviceへ渡す
 *
 * ☑ 必須項目・メール形式・文字数境界の検証
 *   → 空文字・null・未指定・半角スペースのみを拒否
 *   → 名前50文字、email255文字、件名100文字、本文1000文字の境界
 *   → 入力不正時は400と項目別エラーを返し、Serviceを呼ばない
 *
 * ☑ CSRF保護
 *   → トークンなし・不正トークンは403と共通JSONで拒否
 *   → 拒否時はServiceを呼ばない
 *   → 匿名で取得したトークンと同じセッションで受付できる
 *   → トークン取得レスポンスのCache-Control: no-storeを確認
 *
 * ☑ 不正なJSONへの安全な400応答
 *   → 固定メッセージを返し、入力本文・例外詳細を含めない
 *   → Serviceを呼ばない
 *
 * ☑ お問い合わせ保存失敗への安全な500応答
 *   → ContactSaveExceptionをモックで発生させる
 *   → 固定メッセージを返し、原因例外の内部情報を含めない
 *
 * ☑ 管理APIへのアクセス拒否
 *   → 未ログインのGETは401と共通JSON
 *   → 一般ユーザーのGETは403と共通JSON
 *   → いずれもリダイレクトしない
 *
 * ☑ 管理者のアクセス許可
 *   → テスト専用の管理APIへGETでアクセスできる
 *   → 有効なCSRFトークン付きのPOSTでアクセスできる
 *   → 200とテスト用Controllerの応答を確認する
 *
 * ☑ 旧MVCのSecurity応答を維持
 *   → 未ログインでは既存の誘導先へ302リダイレクトする
 *   → 一般ユーザーの管理画面へのアクセスは標準の403で拒否する
 *   → 標準403ではAPI用の共通JSONを返さない
 *
 * 【テストの範囲】
 * 実際のSecurityConfigを使用します。
 * ContactSubmissionServiceとUserRepositoryはモックを使用するため、
 * DB保存・コミット・メール送信は、このクラスでは検証しません。
 *
 * 管理者のアクセス許可は、テスト専用Controllerで確認します。
 * 管理機能の業務処理や、実際の管理APIの完成を保証するものではありません。
 *
 * 旧MVCはSecurityによるリダイレクト・拒否応答までを確認します。
 * Thymeleafの画面描画や、サーバーによる最終的なエラー画面は対象外です。
 *
 * 実ブラウザでのCookie送信・CORS・Reactとの接続、
 * ログイン・ログアウト前後のCSRF再取得は後続で確認します。
 */
@WebMvcTest({
        ContactApiController.class,
        CsrfApiController.class
})
@Import({
        SecurityConfig.class,
        ContactApiControllerTest.TestAdminController.class
})
class ContactApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContactSubmissionService submissionService;

    // SecurityConfigの生成に必要な依存先
    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    @DisplayName("正常な入力で受付Serviceを呼び、201と受付完了メッセージを返す")
    void submitContact_withValidRequest_shouldReturnCreated()
            throws Exception {

        mockMvc.perform(post("/api/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "テスト太郎",
                                  "email": "contact@example.com",
                                  "subject": "ログインについて",
                                  "message": "ログイン方法を教えてください。"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().string(
                        "{\"message\":\"お問い合わせを受け付けました。\"}"
                ));

        // ControllerがServiceへ渡した入力値を取り出す
        ArgumentCaptor<ContactRequest> captor =
                ArgumentCaptor.forClass(ContactRequest.class);

        verify(submissionService).submitContact(captor.capture());

        ContactRequest request = captor.getValue();

        assertThat(request.getName())
                .isEqualTo("テスト太郎");

        assertThat(request.getEmail())
                .isEqualTo("contact@example.com");

        assertThat(request.getSubject())
                .isEqualTo("ログインについて");

        assertThat(request.getMessage())
                .isEqualTo("ログイン方法を教えてください。");
    }

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("入力不正の場合は400と項目別エラーを返し、受付Serviceを呼ばない")
void submitContact_withInvalidRequest_shouldReturnBadRequest()
        throws Exception {

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "",
                              "email": "invalid-email",
                              "subject": "",
                              "message": ""
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message")
                    .value("入力内容に誤りがあります。"))
            .andExpect(jsonPath("$.path").value("/api/contacts"))
            .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors.subject").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors.message").isNotEmpty());

    // 入力検証で拒否され、保存・通知を担当するServiceへ進まない
    verifyNoInteractions(submissionService);
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("名前が上限の50文字なら受け付ける")
void submitContact_withNameAtMaxLength_shouldReturnCreated()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setName("あ".repeat(50));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message")
                    .value("お問い合わせを受け付けました。"));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    // 入力された50文字が、切り詰められずに渡ることも確認
    assertThat(captor.getValue().getName())
            .isEqualTo(request.getName());
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("名前が上限を超える51文字なら拒否する")
void submitContact_withNameOverMaxLength_shouldReturnBadRequest()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setName("あ".repeat(51));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());

    verifyNoInteractions(submissionService);
}

/**
 * 入力条件を満たすお問い合わせデータを作ります。
 * 各テストで、確認したい項目だけを変更して使用します。
 */
private ContactRequest createValidRequest() {

    ContactRequest request = new ContactRequest();

    request.setName("テスト太郎");
    request.setEmail("contact@example.com");
    request.setSubject("ログインについて");
    request.setMessage("ログイン方法を教えてください。");

    return request;
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("件名が上限の100文字なら受け付ける")
void submitContact_withSubjectAtMaxLength_shouldReturnCreated()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setSubject("あ".repeat(100));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message")
                    .value("お問い合わせを受け付けました。"));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    // 件名が切り詰められず、そのままServiceへ渡ることを確認
    assertThat(captor.getValue().getSubject())
            .isEqualTo(request.getSubject());
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("件名が上限を超える101文字なら拒否する")
void submitContact_withSubjectOverMaxLength_shouldReturnBadRequest()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setSubject("あ".repeat(101));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.subject").isNotEmpty());

    verifyNoInteractions(submissionService);
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("本文が上限の1000文字なら受け付ける")
void submitContact_withMessageAtMaxLength_shouldReturnCreated()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setMessage("あ".repeat(1000));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message")
                    .value("お問い合わせを受け付けました。"));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    // 本文が切り詰められず、そのままServiceへ渡ることを確認
    assertThat(captor.getValue().getMessage())
            .isEqualTo(request.getMessage());
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("本文が上限を超える1001文字なら拒否する")
void submitContact_withMessageOverMaxLength_shouldReturnBadRequest()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setMessage("あ".repeat(1001));

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.message").isNotEmpty());

    verifyNoInteractions(submissionService);
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("メールアドレスが255文字で入力条件を満たす場合は受け付ける")
void submitContact_withEmailAtMaxLength_shouldReturnCreated()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setEmail(createBoundaryEmail(58));

    // テストデータ自体の長さと、入力条件を満たすことを確認
    assertThat(request.getEmail()).hasSize(255);
    assertThat(validator.validateProperty(request, "email")).isEmpty();

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message")
                    .value("お問い合わせを受け付けました。"));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    assertThat(captor.getValue().getEmail())
            .isEqualTo(request.getEmail());
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("メールアドレスが256文字の場合は文字数制限で拒否する")
void submitContact_withEmailOverMaxLength_shouldReturnBadRequest()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setEmail(createBoundaryEmail(59));

    assertThat(request.getEmail()).hasSize(256);

    // @Emailではなく、@Sizeだけに違反することを確認
    var violations = validator.validateProperty(request, "email");

    assertThat(violations).hasSize(1);

    var violation = violations.iterator().next();

    assertThat(
            violation.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
    ).isEqualTo(Size.class);

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.email")
                    .value(violation.getMessage()));

    verifyNoInteractions(submissionService);
}

/**
 * メールアドレスの文字数境界を確認するためのデータを作ります。
 *
 * @より前やドメインの各部分を長くしすぎないよう、
 * ドットで区切った複数の部分に文字を分けています。
 *
 * variableLabelLengthが58なら全体255文字、
 * 59なら全体256文字になります。
 * 実際のメール送信には使用しません。
 */
private String createBoundaryEmail(int variableLabelLength) {

    return "a".repeat(64)
            + "@"
            + "b".repeat(63)
            + "."
            + "c".repeat(63)
            + "."
            + "d".repeat(variableLabelLength)
            + ".com";
}

@ParameterizedTest(name = "{0}がnullなら拒否する")
@ValueSource(strings = {"name", "email", "subject", "message"})
@WithMockUser(username = "user@example.com", roles = "USER")
void submitContact_withNullField_shouldReturnBadRequest(
        String fieldName) throws Exception {

    ObjectNode json = objectMapper.valueToTree(createValidRequest());

    // 項目は存在するが、値がnullのJSONを作る
    json.putNull(fieldName);

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(json)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors." + fieldName)
                    .isNotEmpty());

    verifyNoInteractions(submissionService);
}

@ParameterizedTest(name = "{0}が未指定なら拒否する")
@ValueSource(strings = {"name", "email", "subject", "message"})
@WithMockUser(username = "user@example.com", roles = "USER")
void submitContact_withMissingField_shouldReturnBadRequest(
        String fieldName) throws Exception {

    ObjectNode json = objectMapper.valueToTree(createValidRequest());

    // JSONから項目そのものを取り除く
    json.remove(fieldName);

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(json)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors." + fieldName)
                    .isNotEmpty());

    verifyNoInteractions(submissionService);
}

@ParameterizedTest(name = "{0}が半角スペースだけなら拒否する")
@ValueSource(strings = {"name", "email", "subject", "message"})
@WithMockUser(username = "user@example.com", roles = "USER")
void submitContact_withWhitespaceOnlyField_shouldReturnBadRequest(
        String fieldName) throws Exception {

    ObjectNode json = objectMapper.valueToTree(createValidRequest());

    // 空文字ではないが、内容が半角スペースだけの値
    json.put(fieldName, "   ");

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(json)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors." + fieldName)
                    .isNotEmpty());

    verifyNoInteractions(submissionService);
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("メールアドレスが空文字なら拒否する")
void submitContact_withEmptyEmail_shouldReturnBadRequest()
        throws Exception {

    ContactRequest request = createValidRequest();
    request.setEmail("");

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());

    verifyNoInteractions(submissionService);
}

@Test
@WithAnonymousUser
@DisplayName("未ログインでも有効なCSRFトークンがあれば受け付ける")
void submitContact_asAnonymousWithCsrf_shouldReturnCreated()
        throws Exception {

    ContactRequest request = createValidRequest();

    mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andExpect(content().string(
                    "{\"message\":\"お問い合わせを受け付けました。\"}"
            ));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    ContactRequest actualRequest = captor.getValue();

    assertThat(actualRequest.getName())
            .isEqualTo(request.getName());

    assertThat(actualRequest.getEmail())
            .isEqualTo(request.getEmail());

    assertThat(actualRequest.getSubject())
            .isEqualTo(request.getSubject());

    assertThat(actualRequest.getMessage())
            .isEqualTo(request.getMessage());
}

@Test
@WithAnonymousUser
@DisplayName("未ログインでCSRFトークンがない場合は403で拒否する")
void submitContact_asAnonymousWithoutCsrf_shouldReturnForbidden()
        throws Exception {

    ContactRequest request = createValidRequest();

    // csrf()を付けずに送信する
    mockMvc.perform(post("/api/contacts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message")
            .value("このリクエストは許可されていません。"))
            .andExpect(jsonPath("$.path")
            .value("/api/contacts"));

    verifyNoInteractions(submissionService);
}

@Test
@WithAnonymousUser
@DisplayName("未ログインでCSRFトークンが不正な場合は403で拒否する")
void submitContact_asAnonymousWithInvalidCsrf_shouldReturnForbidden()
        throws Exception {

    ContactRequest request = createValidRequest();

    mockMvc.perform(post("/api/contacts")
                    .with(csrf().useInvalidToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                   .andExpect(status().isForbidden())
                   .andExpect(content()
                   .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                   .andExpect(header().doesNotExist("Location"))
                   .andExpect(jsonPath("$.status").value(403))
                   .andExpect(jsonPath("$.error").value("Forbidden"))
                   .andExpect(jsonPath("$.message")
                   .value("このリクエストは許可されていません。"))
                   .andExpect(jsonPath("$.path")
                   .value("/api/contacts")); 

    verifyNoInteractions(submissionService);
}

@Test
@WithAnonymousUser
@DisplayName("匿名で取得したCSRFトークンと同じセッションでお問い合わせを送信できる")
void submitContact_withFetchedCsrfToken_shouldReturnCreated()
        throws Exception {

    // 1. 実際の取得APIを呼ぶ。テスト用のcsrf()は使用しない
    MvcResult csrfResult = mockMvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.headerName").isNotEmpty())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

    JsonNode csrfResponse = objectMapper.readTree(
            csrfResult.getResponse().getContentAsString()
    );

    String headerName = csrfResponse.get("headerName").asText();
    String token = csrfResponse.get("token").asText();

    MockHttpSession session =
            (MockHttpSession) csrfResult.getRequest().getSession(false);

    assertThat(session).isNotNull();

    // トークン取得だけでは受付処理が呼ばれない
    verifyNoInteractions(submissionService);

    // 2. 取得時と同じセッションと、返されたトークンを使って送信する
    ContactRequest request = createValidRequest();

    mockMvc.perform(post("/api/contacts")
                    .session(session)
                    .header(headerName, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().string(
                    "{\"message\":\"お問い合わせを受け付けました。\"}"
            ));

    ArgumentCaptor<ContactRequest> captor =
            ArgumentCaptor.forClass(ContactRequest.class);

    verify(submissionService).submitContact(captor.capture());

    ContactRequest actualRequest = captor.getValue();

    assertThat(actualRequest.getName()).isEqualTo(request.getName());
    assertThat(actualRequest.getEmail()).isEqualTo(request.getEmail());
    assertThat(actualRequest.getSubject()).isEqualTo(request.getSubject());
    assertThat(actualRequest.getMessage()).isEqualTo(request.getMessage());
}


@Test
@WithAnonymousUser
@DisplayName("JSONの構文が不正な場合は安全な400応答を返し、Serviceを呼ばない")
void submitContact_withMalformedJson_shouldReturnBadRequest()
        throws Exception {

    // 最後の閉じ括弧がない、不正なJSON
    String malformedJson = """
            {
              "name": "テスト太郎",
              "email": "contact@example.com",
              "subject": "お問い合わせ",
              "message": "本文がレスポンスへ漏れないことを確認"
            """;

    MvcResult result = mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andReturn();

    // JSON全体を比較し、余分な例外情報や入力本文がないことも確認
    JsonNode actualResponse = objectMapper.readTree(
            result.getResponse().getContentAsString()
    );

    JsonNode expectedResponse = objectMapper.readTree("""
            {
              "status": 400,
              "error": "Bad Request",
              "message": "リクエストの形式が正しくありません。",
              "path": "/api/contacts"
            }
            """);

    assertThat(actualResponse).isEqualTo(expectedResponse);

    verifyNoInteractions(submissionService);
}

@Test
@WithAnonymousUser
@DisplayName("お問い合わせ保存失敗時は内部情報を含まない500応答を返す")
void submitContact_whenSaveFails_shouldReturnSafeInternalServerError()
        throws Exception {

    ContactRequest request = createValidRequest();

    // レスポンスへ出してはいけない内部情報を、原因例外に設定
    ContactSaveException failure = new ContactSaveException(
            new DataAccessResourceFailureException(
                    "INTERNAL_DB_DETAIL_FOR_TEST"
            )
    );

    doThrow(failure)
            .when(submissionService)
            .submitContact(any(ContactRequest.class));

    MvcResult result = mockMvc.perform(post("/api/contacts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andReturn();

    JsonNode actualResponse = objectMapper.readTree(
            result.getResponse().getContentAsString()
    );

    JsonNode expectedResponse = objectMapper.readTree("""
            {
              "status": 500,
              "error": "Internal Server Error",
              "message": "お問い合わせを受け付けられませんでした。時間をおいて再度お試しください。",
              "path": "/api/contacts"
            }
            """);

    // レスポンス全体を比較し、原因例外などの余分な情報がないことを確認
    assertThat(actualResponse).isEqualTo(expectedResponse);

    verify(submissionService).submitContact(any(ContactRequest.class));
}

@Test
@WithAnonymousUser
@DisplayName("未ログインで管理APIへアクセスすると401と共通JSONを返す")
void adminApi_whenAnonymous_shouldReturnUnauthorizedJson()
        throws Exception {

    // Controllerへ到達する前のSecurityによる拒否を検証します。
    mockMvc.perform(get("/api/admin/contacts"))
            .andExpect(status().isUnauthorized())
            .andExpect(content()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message")
                    .value("ログインが必要です。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/admin/contacts"));
}

@Test
@WithAnonymousUser      
@DisplayName("未ログインで旧MVCの管理画面へアクセスすると既存の誘導先へリダイレクトする")
void adminMvc_whenAnonymous_shouldKeepExistingRedirect()
        throws Exception {

    mockMvc.perform(get("/admin/users"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl(
                    "http://localhost/posts?error=unauthorized"
            ));
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("一般ユーザーが管理APIへアクセスすると403と共通JSONを返す")
void adminApi_whenRegularUser_shouldReturnForbiddenJson()
        throws Exception {

    mockMvc.perform(get("/api/admin/contacts"))
            .andExpect(status().isForbidden())
            .andExpect(content()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message")
                    .value("このリクエストは許可されていません。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/admin/contacts"));
}


@Test
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
@DisplayName("管理者は管理APIのGETへアクセスできる")
void adminApi_whenAdmin_shouldAllowGet() throws Exception {

    mockMvc.perform(get("/api/admin/security-check"))
            .andExpect(status().isOk())
            .andExpect(content().string("admin-ok"));
}

@Test
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
@DisplayName("管理者は有効なCSRFトークン付きで管理APIのPOSTへアクセスできる")
void adminApi_whenAdminWithCsrf_shouldAllowPost() throws Exception {

    mockMvc.perform(post("/api/admin/security-check")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string("admin-ok"));
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
@DisplayName("一般ユーザーの旧MVC管理画面へのアクセスは標準の403で拒否する")
void adminMvc_whenRegularUser_shouldKeepStandardForbidden()
        throws Exception {

    MvcResult result = mockMvc.perform(get("/admin/users"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Location"))
            .andReturn();

    // API用JSONではなく、標準のsendErrorによる拒否であることを確認
    assertThat(result.getResponse().getErrorMessage())
            .isEqualTo("Forbidden");

    assertThat(result.getResponse().getContentAsString())
            .isEmpty();
}



/**
 * 管理APIの認可を確認するためのテスト専用Controllerです。
 * DB操作や管理機能の業務処理は行いません。
 */
@RestController
public static class TestAdminController {

    @GetMapping("/api/admin/security-check")
    public String get() {
        return "admin-ok";
    }

    @PostMapping("/api/admin/security-check")
    public String post() {
        return "admin-ok";
    }
}
}