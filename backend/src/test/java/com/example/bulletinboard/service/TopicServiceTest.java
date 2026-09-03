package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.TopicRepository;

/*
 * 【クラスの役割】
 * TopicServiceのビジネスロジックを検証する単体テストクラスです。
 *
 * TopicRepositoryとAnswerRepositoryをMockitoでモック化し、
 * 実際のデータベースには接続せずにService層だけをテストします。
 *
 * 【主な検証内容】
 * - お題タイトルの部分一致検索
 * - ページネーション
 * - ソート順
 * - 負のページ番号の補正
 * - 削除されていないTopic一覧の取得
 * - 投稿者本人によるTopicの論理削除
 *
 * 【設計上のポイント】
 * - 検索対象はお題タイトルのみです。
 * - 検索方式は部分一致のみです。
 * - 前方一致・後方一致のテストは新仕様では不要です。
 * - Topic編集や削除権限に関する追加ケースは、
 *   feature/topic-answer-apiのService Test工程で拡充します。
 */


@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

   @Mock
   private TopicRepository topicRepository;

   @Mock
   private AnswerRepository answerRepository;

   @InjectMocks
   private TopicService topicService;

    @Test
    @DisplayName("お題タイトルを部分一致検索できること")
    void searchTopics_PartialMatch_ShouldReturnResults() {

        Page<Topic> dummyPage =
            new PageImpl<>(List.of(new Topic()));

        when(
            topicRepository
                .findByTitleContainingAndDeletedAtIsNull(
                    eq("Java"),
                    any(Pageable.class)
                )
        ).thenReturn(dummyPage);

        Page<Topic> results =
            topicService.searchTopics(
                0,
                "Java",
                "createdAt",
                "desc"
            );

        verify(
            topicRepository,
            times(1)
        ).findByTitleContainingAndDeletedAtIsNull(
            eq("Java"),
            any(Pageable.class)
        );

        assertThat(results.getContent())
            .hasSize(1);
    }

    @Test
    @DisplayName("sortOrderにascを指定すると昇順のPageableがRepositoryへ渡されること")
    void searchTopics_SortAscending_ShouldPassAscendingPageable() {

        ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

        Page<Topic> dummyPage =
            new PageImpl<>(List.of(new Topic()));

        when(
            topicRepository
                .findByTitleContainingAndDeletedAtIsNull(
                    eq("Java"),
                    any(Pageable.class)
                )
        ).thenReturn(dummyPage);

        topicService.searchTopics(
            0,
            "Java",
            "createdAt",
            "asc"
        );

        verify(
            topicRepository
        ).findByTitleContainingAndDeletedAtIsNull(
            eq("Java"),
            pageableCaptor.capture()
        );

        Pageable capturedPageable =
            pageableCaptor.getValue();

        assertThat(
            capturedPageable
                .getSort()
                .getOrderFor("createdAt")
                .getDirection()
        ).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("負のページ番号が渡された場合は0ページ目に補正されること")
    void searchTopics_NegativePage_ShouldCorrectToZero() {

        ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

        Page<Topic> dummyPage =
            new PageImpl<>(List.of(new Topic()));

        when(
            topicRepository
                .findByTitleContainingAndDeletedAtIsNull(
                    eq("Java"),
                    any(Pageable.class)
                )
        ).thenReturn(dummyPage);

        topicService.searchTopics(
            -1,
            "Java",
            "createdAt",
            "desc"
        );

        verify(
            topicRepository
        ).findByTitleContainingAndDeletedAtIsNull(
            eq("Java"),
            pageableCaptor.capture()
        );

        Pageable capturedPageable =
            pageableCaptor.getValue();

        assertThat(
            capturedPageable.getPageNumber()
        ).isEqualTo(0);
    }

    @Test
    @DisplayName("削除されていないTopic一覧を取得できること")
    void findAll_ShouldReturnNonDeletedTopics() {

        Page<Topic> dummyPage =
            new PageImpl<>(
                List.of(
                    new Topic(),
                    new Topic()
                )
            );

        when(
            topicRepository.findByDeletedAtIsNull(
                any(Pageable.class)
            )
        ).thenReturn(dummyPage);

        Page<Topic> results =
            topicService.findAll(
                0,
                "createdAt",
                "desc"
            );

        verify(
            topicRepository,
            times(1)
        ).findByDeletedAtIsNull(
            any(Pageable.class)
        );

        assertThat(results.getContent())
            .hasSize(2);
    }

    @Test
    @DisplayName("投稿者本人が指定したTopicを論理削除できること")
    void deleteById_ShouldSetDeletedAt() {

    User topicUser = new User();
    topicUser.setEmail("testuser01@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);

    when(
        topicRepository
            .findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    when(
        topicRepository.save(any(Topic.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    topicService.deleteById(
        1L,
        "testuser01@example.com",
        false
    );

    assertThat(topic.getDeletedAt())
        .isNotNull();

    verify(
        topicRepository,
        times(1)
    ).save(topic);
}
}