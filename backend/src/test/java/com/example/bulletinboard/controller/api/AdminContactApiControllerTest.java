package com.example.bulletinboard.controller.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.exception.ContactNotFoundException;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminContactService;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ詳細取得APIについて、
 * HTTPステータスとJSONレスポンスを検証するテストクラスです。
 *
 * 【現在の検証内容】
 * - 対象不存在時に404と共通エラーJSONを返すこと
 * - 正常取得時に200と詳細情報8項目を返すこと
 *
 * 【テストの構成】
 * - Controller・SecurityConfig・GlobalExceptionHandlerは実物を使用します。
 * - AdminContactServiceとUserRepositoryはモックに置き換えます。
 * - @WithMockUserで管理者の認証状態を再現します。
 *
 * 【テストの範囲】
 * HTTPリクエストの受付、DTO変換、例外ハンドリングを確認します。
 * 実DBの検索処理や、実際のログイン処理は検証しません。
 * 匿名・一般ユーザーのアクセス拒否は、後続のテストで確認します。
 */
@WebMvcTest(AdminContactApiController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AdminContactApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * お問い合わせ管理Serviceのモック。
     * テストごとに取得結果や例外を設定します。
     */
    @MockitoBean
   private AdminContactService adminContactService;

    /**
     * SecurityConfigが依存するUserRepositoryのモック。
     */
    @MockitoBean
    private UserRepository userRepository;

    /**
     * Serviceが対象不存在の例外を返した場合に、
     * GlobalExceptionHandlerが404と共通JSONへ変換することを確認します。
     */
    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    @DisplayName("対象のお問い合わせが存在しない場合は404と共通エラーJSONを返す")
    void getContact_whenNotFound_shouldReturn404() throws Exception {

        // Given：対象不存在をServiceの例外で再現する
        when(adminContactService.getById(999L))
                .thenThrow(new ContactNotFoundException());

        // When・Then：管理者として詳細APIを呼び、404とJSONを確認する
        mockMvc.perform(
                get("/api/admin/contacts/999")
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(
                        jsonPath("$.message").value(
                               "指定されたお問い合わせが見つかりません。"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/admin/contacts/999"
                        )
                );

        // URLのIDがServiceへ渡されたことを確認する
        verify(adminContactService).getById(999L);
    }

    /**
     * Serviceから取得したContactが詳細DTOへ変換され、
     * 決定済みの8項目をJSONで返すことを確認します。
     */
    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    @DisplayName("管理者がお問い合わせ詳細を取得すると200と詳細8項目を返す")
    void getContact_whenFound_shouldReturn200() throws Exception {

        // Given：Serviceから返されるお問い合わせを用意する
        Contact contact = new Contact();
        contact.setId(1L);
        contact.setName("テスト太郎");
        contact.setEmail("user@example.com");
        contact.setSubject("ログインについて");
        contact.setMessage("ログイン方法を教えてください。");
        contact.setStatus(ContactStatus.UNANSWERED);
        contact.setCreatedAt(
                LocalDateTime.of(2026, 9, 20, 10, 0, 0)
        );
        contact.setUpdatedAt(
                LocalDateTime.of(2026, 9, 20, 11, 0, 0)
        );

        when(adminContactService.getById(1L))
                .thenReturn(contact);

        // When・Then：200と詳細情報8項目を確認する
        mockMvc.perform(
                get("/api/admin/contacts/1")
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("テスト太郎"))
                .andExpect(
                        jsonPath("$.email").value("user@example.com")
                )
                .andExpect(
                        jsonPath("$.subject").value("ログインについて")
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "ログイン方法を教えてください。"
                        )
                )
                .andExpect(
                        jsonPath("$.status").value("UNANSWERED")
                )
                .andExpect(
                        jsonPath("$.createdAt").value(
                                "2026-09-20T10:00:00"
                        )
                )
                .andExpect(
                        jsonPath("$.updatedAt").value(
                                "2026-09-20T11:00:00"
                        )
                );

        // URLのIDがServiceへ渡されたことを確認する
        verify(adminContactService).getById(1L);
    }

    /**
 * パラメータ省略時にpage=0・size=20・状態指定なしで取得し、
 * 一覧用の5項目とページ情報を返すことを確認します。
 */
@Test
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
@DisplayName("管理者が一覧を取得すると標準のページ条件で200と一覧JSONを返す")
void getContacts_withoutParameters_shouldReturnDefaultPage() throws Exception {

    // Given：一覧に返すお問い合わせを用意する
    Contact contact = new Contact();
    contact.setId(1L);
    contact.setName("テスト太郎");
    contact.setEmail("user@example.com");
    contact.setSubject("ログインについて");
    contact.setMessage("ログイン方法を教えてください。");
    contact.setStatus(ContactStatus.UNANSWERED);
    contact.setCreatedAt(
            LocalDateTime.of(2026, 9, 20, 10, 0, 0)
    );
    contact.setUpdatedAt(
            LocalDateTime.of(2026, 9, 20, 11, 0, 0)
    );

    Page<Contact> contacts = new PageImpl<>(
            List.of(contact),
            PageRequest.of(0, 20),
            1
    );

    when(adminContactService.findContacts(0, 20, null))
            .thenReturn(contacts);

    // When・Then：パラメータを省略して一覧APIを呼ぶ
    mockMvc.perform(
            get("/api/admin/contacts")
                    .accept(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isOk())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )

            // ページ情報を確認する
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.content.length()").value(1))

            // 一覧用の5項目を確認する
            .andExpect(jsonPath("$.content[0].length()").value(5))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(
                    jsonPath("$.content[0].name").value("テスト太郎")
            )
            .andExpect(
                    jsonPath("$.content[0].subject").value("ログインについて")
            )
            .andExpect(
                    jsonPath("$.content[0].status").value("UNANSWERED")
            )
            .andExpect(
                    jsonPath("$.content[0].createdAt")
                            .value("2026-09-20T10:00:00")
            )

            // 詳細専用の項目が一覧に含まれないことを確認する
            .andExpect(jsonPath("$.content[0].email").doesNotExist())
            .andExpect(jsonPath("$.content[0].message").doesNotExist())
            .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());

    // 省略時の値がServiceへ渡されたことを確認する
    verify(adminContactService).findContacts(0, 20, null);
}

/**
 * 指定したページ番号・件数・ステータスがServiceへ渡り、
 * 取得結果とページ情報を返すことを確認します。
 *
 * DBでの実際の絞り込みはRepositoryテストで確認します。
 */
@Test
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
@DisplayName("ページ番号・件数・状態を指定して一覧を取得できる")
void getContacts_withParameters_shouldPassConditionsToService()
        throws Exception {

    // Given：対応中の一覧データを用意する
    Contact contact = new Contact();
    contact.setId(12L);
    contact.setName("テスト太郎");
    contact.setSubject("ログインについて");
    contact.setStatus(ContactStatus.IN_PROGRESS);
    contact.setCreatedAt(
            LocalDateTime.of(2026, 9, 20, 10, 0, 0)
    );

    // 全11件のうち、2ページ目に1件ある状態を再現する
    Page<Contact> contacts = new PageImpl<>(
            List.of(contact),
            PageRequest.of(1, 10),
            11
    );

    when(adminContactService.findContacts(
            1,
            10,
            ContactStatus.IN_PROGRESS
    )).thenReturn(contacts);

    // When・Then：検索条件を指定してAPIを呼ぶ
    mockMvc.perform(
            get("/api/admin/contacts")
                    .param("page", "1")
                    .param("size", "10")
                    .param("status", "IN_PROGRESS")
                    .accept(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isOk())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(11))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(12))
            .andExpect(
                    jsonPath("$.content[0].status").value("IN_PROGRESS")
            );

    // 文字列のstatusがEnumに変換されて渡ることも確認する
    verify(adminContactService).findContacts(
            1,
            10,
            ContactStatus.IN_PROGRESS
    );
}


/**
 * 該当データなし・最終ページ超過の場合も、
 * 200と空のcontent、正しいページ情報を返すことを確認します。
 *
 * Serviceの取得結果はモックで再現します。
 */
@ParameterizedTest(name = "page={0}, totalElements={1}の場合に空一覧を返す")
@CsvSource({
        "0, 0, 0",
        "2, 21, 2"
})
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
void getContacts_whenPageIsEmpty_shouldReturn200WithEmptyContent(
        int page,
        long totalElements,
        int totalPages) throws Exception {

    // Given：
    // 1行目はデータ0件。
    // 2行目は全21件・全2ページに対して3ページ目を要求した状態。
    Page<Contact> contacts = new PageImpl<>(
            List.of(),
            PageRequest.of(page, 20),
            totalElements
    );

    when(adminContactService.findContacts(page, 20, null))
            .thenReturn(contacts);

    // When・Then：空一覧でも404ではなく200を返す
    mockMvc.perform(
            get("/api/admin/contacts")
                    .param("page", String.valueOf(page))
                    .accept(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isOk())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(page))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(totalElements))
            .andExpect(jsonPath("$.totalPages").value(totalPages));

    verify(adminContactService).findContacts(page, 20, null);
}

/**
 * 空文字・空白のみ・未知の値・小文字のステータスを拒否し、
 * Serviceへ処理を渡さないことを確認します。
 */
@ParameterizedTest(name = "status=[{0}]は400で拒否する")
@ValueSource(strings = {"", "   ", "UNKNOWN", "unanswered"})
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
void getContacts_withInvalidStatus_shouldReturn400(
        String invalidStatus) throws Exception {

    mockMvc.perform(
            get("/api/admin/contacts")
                    .param("status", invalidStatus)
                    .accept(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isBadRequest())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(
                    jsonPath("$.message").value(
                            "指定されたステータスは無効です。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value("/api/admin/contacts")
            );

    // Controllerで拒否され、検索処理には進まない
    verifyNoInteractions(adminContactService);
}

/**
 * ページ番号・件数の検証でServiceが例外を返した場合、
 * 400と共通エラーJSONになることを確認します。
 *
 * 範囲外の判定そのものはAdminContactServiceTestで検証済みです。
 * このテストではServiceの例外をモックで再現します。
 */
@ParameterizedTest(name = "page={0}, size={1}の場合に400を返す")
@CsvSource({
        "-1, 20, ページ番号は0以上で指定してください。",
        "0, 0, 1ページあたりの件数は1～100で指定してください。",
        "0, 101, 1ページあたりの件数は1～100で指定してください。"
})
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
void getContacts_withOutOfRangeParameters_shouldReturn400(
        int page,
        int size,
        String expectedMessage) throws Exception {

    // Given：Serviceの入力検証エラーを再現する
    when(adminContactService.findContacts(page, size, null))
            .thenThrow(new IllegalArgumentException(expectedMessage));

    // When・Then：例外が400と共通JSONへ変換されることを確認する
    mockMvc.perform(
            get("/api/admin/contacts")
                    .param("page", String.valueOf(page))
                    .param("size", String.valueOf(size))
                    .accept(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isBadRequest())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(expectedMessage))
            .andExpect(
                    jsonPath("$.path").value("/api/admin/contacts")
            );

    // 範囲の検証はServiceで行うため、Serviceは呼ばれる
    verify(adminContactService).findContacts(page, size, null);
}

/**
 * page・sizeを整数へ変換できない場合に、
 * 共通の400応答を返し、Serviceを呼ばないことを確認します。
 */
@ParameterizedTest
@CsvSource({
        "page, abc",
        "page, 1.5",
        "page, 2147483648",
        "size, abc",
        "size, 1.5",
        "size, 2147483648"
})
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
@DisplayName("ページ条件の型変換に失敗した場合は400を返しServiceを呼ばない")
void getContacts_whenParameterTypeIsInvalid_shouldReturnBadRequest(
        String parameterName,
        String value) throws Exception {

    mockMvc.perform(
            get("/api/admin/contacts")
                    .param(parameterName, value)
    )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(
                    "リクエストのパラメータ形式が正しくありません。"
            ))
            .andExpect(jsonPath("$.path").value("/api/admin/contacts"));

    // Controllerの引数へ変換できないため、業務処理には進まない
    verifyNoInteractions(adminContactService);
}

/**
 * IDをLongへ変換できない場合に、
 * 共通の400応答を返し、Serviceを呼ばないことを確認します。
 */
@ParameterizedTest
@ValueSource(strings = {
        "abc",
        "1.5",
        "9223372036854775808"
})
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
@DisplayName("IDの型変換に失敗した場合は400を返しServiceを呼ばない")
void getContact_whenIdTypeIsInvalid_shouldReturnBadRequest(
        String id) throws Exception {

    mockMvc.perform(
            get("/api/admin/contacts/{id}", id)
    )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(
                    "リクエストのパラメータ形式が正しくありません。"
            ))
            .andExpect(jsonPath("$.path").value(
                    "/api/admin/contacts/" + id
            ));

    // IDを変換できないため、詳細取得処理には進まない
    verifyNoInteractions(adminContactService);
}
}
