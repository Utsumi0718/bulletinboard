package com.example.bulletinboard.controller.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.LikeConflictException;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.LikeService;
/**
 * 【クラスの役割】
 * LikeApiControllerのWebレイヤーを検証するテストクラスです。
 *
 * POST /api/answers/{answerId}/like に対して
 * MockMvcを利用してHTTPリクエストを再現し、
 * HTTP StatusやJSON Response、
 * Spring Securityによる認証・CSRF制御を確認します。
 *
 * Likeに関する業務ロジック自体はLikeServiceTestで検証し、
 * このクラスではControllerからServiceへの値の受け渡しと
 * Webレスポンスを中心に検証します。
 */
@WebMvcTest(LikeApiController.class)
@Import(SecurityConfig.class)
class LikeApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("Like追加成功時に200 OKとliked=true・最新Like件数が返ること")
    void toggleLike_WhenLikeAdded_ShouldReturnOkAndLikeResponse()
            throws Exception {

        // Given
        LikeResponse response =
                new LikeResponse(
                        true,
                        1L
                );

        when(
                likeService.toggleLike(
                        10L,
                        "testuser@example.com"
                )
        ).thenReturn(response);

        // When & Then
        mockMvc.perform(
                post("/api/answers/10/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.liked").value(true)
                )
                .andExpect(
                        jsonPath("$.likeCount").value(1)
                );

        verify(
                likeService,
                times(1)
        ).toggleLike(
                10L,
                "testuser@example.com"
        );
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("Like解除成功時に200 OKとliked=false・最新Like件数が返ること")
    void toggleLike_WhenLikeRemoved_ShouldReturnOkAndLikeResponse()
        throws Exception {

    // Given
    LikeResponse response =
            new LikeResponse(
                    false,
                    4L
            );

    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenReturn(response);

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isOk())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.liked").value(false)
            )
            .andExpect(
                    jsonPath("$.likeCount").value(4)
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}

@Test
@DisplayName("未認証でLike APIへアクセスするとログイン要求へリダイレクトされLikeServiceは実行されないこと")
void toggleLike_Unauthenticated_ShouldRedirectAndNotCallService()
        throws Exception {

    mockMvc.perform(
            post("/api/answers/10/like")
                    .with(csrf())
    )
            .andExpect(status().is3xxRedirection())
            .andExpect(
               redirectedUrl(
                "http://localhost/posts?error=unauthorized"
            )
);
    verify(
            likeService,
            never()
    ).toggleLike(
            anyLong(),
            anyString()
    );
}

@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("CSRF TokenなしでLike APIへPOSTすると403 ForbiddenとなりLikeServiceは実行されないこと")
void toggleLike_WithoutCsrf_ShouldReturnForbiddenAndNotCallService()
        throws Exception {

    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
    )
            .andExpect(status().isForbidden());

    verify(
            likeService,
            never()
    ).toggleLike(
            anyLong(),
            anyString()
    );
}

@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("Answerが存在しない場合に404 Not FoundとErrorResponseが返ること")
void toggleLike_WhenAnswerNotFound_ShouldReturnNotFound()
        throws Exception {

    // Given
    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenThrow(
            new AnswerNotFoundException(
                    "この回答は存在しないか、削除されています。"
            )
    );

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isNotFound())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.status").value(404)
            )
            .andExpect(
                    jsonPath("$.error").value("Not Found")
            )
            .andExpect(
                    jsonPath("$.message").value(
                            "この回答は存在しないか、削除されています。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value(
                            "/api/answers/10/like"
                    )
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}

@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("論理削除済みAnswerへのLikeで404 Not FoundとErrorResponseが返ること")
void toggleLike_WhenAnswerIsDeleted_ShouldReturnNotFound()
        throws Exception {

    // Given
    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenThrow(
            new AnswerNotFoundException(
                    "この回答は存在しないか、削除されています。"
            )
    );

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isNotFound())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.status").value(404)
            )
            .andExpect(
                    jsonPath("$.error").value("Not Found")
            )
            .andExpect(
                    jsonPath("$.message").value(
                            "この回答は存在しないか、削除されています。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value(
                            "/api/answers/10/like"
                    )
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}

@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("論理削除済みTopic配下のAnswerへのLikeで404 Not FoundとErrorResponseが返ること")
void toggleLike_WhenParentTopicIsDeleted_ShouldReturnNotFound()
        throws Exception {

    // Given
    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenThrow(
            new AnswerNotFoundException(
                    "この回答は存在しないか、削除されています。"
            )
    );

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isNotFound())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.status").value(404)
            )
            .andExpect(
                    jsonPath("$.error").value("Not Found")
            )
            .andExpect(
                    jsonPath("$.message").value(
                            "この回答は存在しないか、削除されています。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value(
                            "/api/answers/10/like"
                    )
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}

@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("自分自身のAnswerへのLikeで403 ForbiddenとErrorResponseが返ること")
void toggleLike_WhenLikingOwnAnswer_ShouldReturnForbidden()
        throws Exception {

    // Given
    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenThrow(
            new ForbiddenOperationException(
                    "自分の回答にはいいねできません。"
            )
    );

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isForbidden())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.status").value(403)
            )
            .andExpect(
                    jsonPath("$.error").value("Forbidden")
            )
            .andExpect(
                    jsonPath("$.message").value(
                            "自分の回答にはいいねできません。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value(
                            "/api/answers/10/like"
                    )
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}


@Test
@WithMockUser(username = "testuser@example.com")
@DisplayName("Like登録競合時に409 ConflictとErrorResponseが返ること")
void toggleLike_WhenLikeConflictOccurs_ShouldReturnConflict()
        throws Exception {

    // Given
    when(
            likeService.toggleLike(
                    10L,
                    "testuser@example.com"
            )
    ).thenThrow(
            new LikeConflictException(
                    "この回答にはすでにいいねしています。"
            )
    );

    // When & Then
    mockMvc.perform(
            post("/api/answers/10/like")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
    )
            .andExpect(status().isConflict())
            .andExpect(
                    content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    )
            )
            .andExpect(
                    jsonPath("$.status").value(409)
            )
            .andExpect(
                    jsonPath("$.error").value("Conflict")
            )
            .andExpect(
                    jsonPath("$.message").value(
                            "この回答にはすでにいいねしています。"
                    )
            )
            .andExpect(
                    jsonPath("$.path").value(
                            "/api/answers/10/like"
                    )
            );

    verify(
            likeService,
            times(1)
    ).toggleLike(
            10L,
            "testuser@example.com"
    );
}
}