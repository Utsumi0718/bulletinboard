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
 * - 削除されていないTopicのID取得
 * - Topicの保存
 * - Answerの存在判定
 * - 投稿者本人かつAnswerがないTopicの編集
 * - 投稿者本人以外によるTopic編集の拒否
 * - Answerが存在するTopicの編集拒否
 * - 投稿者本人によるTopicの論理削除
 * - 管理者による他ユーザーTopicの論理削除
 * - 権限のないユーザーによるTopic削除の拒否
 * - 存在しないTopic削除時の例外
 *
 * 【設計上のポイント】
 * - 検索対象はお題タイトルのみです。
 * - 検索方式は部分一致のみです。
 * - 前方一致・後方一致のテストは新仕様では不要です。
 * - Topic編集は投稿者本人かつAnswerが一度も存在しない場合のみ許可します。
 * - Answerの存在判定はAnswerRepository.existsByTopicId()を使用します。
 * - Topic削除は投稿者本人またはROLE_ADMINのみ許可します。
 * - Topic削除は物理削除ではなくdeletedAtを設定する論理削除です。
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

@Test
@DisplayName("投稿者本人かつ回答がないTopicを編集できること")
void updateTopic_OwnerAndNoAnswer_ShouldUpdateTopic() {

    User topicUser = new User();
    topicUser.setEmail("owner@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);
    topic.setTitle("変更前タイトル");
    topic.setImage("before.webp");
    topic.setQuestion("変更前の問題");

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    when(
        answerRepository.existsByTopicId(1L)
    ).thenReturn(false);

    when(
        topicRepository.save(any(Topic.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    Topic updated = topicService.updateTopic(
        1L,
        "owner@example.com",
        "変更後タイトル",
        "after.webp",
        "変更後の問題"
    );

    assertThat(updated.getTitle())
        .isEqualTo("変更後タイトル");

    assertThat(updated.getImage())
        .isEqualTo("after.webp");

    assertThat(updated.getQuestion())
        .isEqualTo("変更後の問題");

    verify(topicRepository, times(1))
        .save(topic);
}

@Test
@DisplayName("投稿者本人以外はTopicを編集できないこと")
void updateTopic_NotOwner_ShouldThrowException() {

    User topicUser = new User();
    topicUser.setEmail("owner@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            () -> topicService.updateTopic(
                1L,
                "other@example.com",
                "タイトル",
                "image.webp",
                "問題"
            )
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("このお題を編集する権限がありません。");

    verify(
        topicRepository,
        org.mockito.Mockito.never()
    ).save(any(Topic.class));
}

@Test
@DisplayName("回答が存在するTopicは編集できないこと")
void updateTopic_AnswerExists_ShouldThrowException() {

    User topicUser = new User();
    topicUser.setEmail("owner@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    when(
        answerRepository.existsByTopicId(1L)
    ).thenReturn(true);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> topicService.updateTopic(
                1L,
                "owner@example.com",
                "タイトル",
                "image.webp",
                "問題"
            )
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("回答が投稿されたお題は編集できません。");

    verify(
        topicRepository,
        org.mockito.Mockito.never()
    ).save(any(Topic.class));
}

@Test
@DisplayName("管理者は他ユーザーのTopicを削除できること")
void deleteById_Admin_ShouldSetDeletedAt() {

    User topicUser = new User();
    topicUser.setEmail("owner@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    when(
        topicRepository.save(any(Topic.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    topicService.deleteById(
        1L,
        "admin@example.com",
        true
    );

    assertThat(topic.getDeletedAt())
        .isNotNull();

    verify(topicRepository, times(1))
        .save(topic);
}

@Test
@DisplayName("投稿者本人でも管理者でもない場合はTopicを削除できないこと")
void deleteById_NotOwnerAndNotAdmin_ShouldThrowException() {

    User topicUser = new User();
    topicUser.setEmail("owner@example.com");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setUser(topicUser);

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            () -> topicService.deleteById(
                1L,
                "other@example.com",
                false
            )
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("このお題を削除する権限がありません。");

    verify(
        topicRepository,
        org.mockito.Mockito.never()
    ).save(any(Topic.class));
}
@Test
@DisplayName("存在しないTopicを削除しようとすると例外になること")
void deleteById_TopicNotFound_ShouldThrowException() {

    when(
        topicRepository.findByIdAndDeletedAtIsNull(999L)
    ).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            () -> topicService.deleteById(
                999L,
                "test@example.com",
                false
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("指定されたお題が存在しません。id=999");

    verify(
        topicRepository,
        org.mockito.Mockito.never()
    ).save(any(Topic.class));
}

@Test
@DisplayName("TopicにAnswerが存在する場合はtrueを返すこと")
void hasAnyAnswer_ShouldReturnTrueWhenAnswerExists() {

    when(
        answerRepository.existsByTopicId(1L)
    ).thenReturn(true);

    boolean result =
        topicService.hasAnyAnswer(1L);

    assertThat(result).isTrue();

    verify(
        answerRepository,
        times(1)
    ).existsByTopicId(1L);
}

@Test
@DisplayName("削除されていないTopicをIDで取得できること")
void findById_ShouldReturnTopic() {

    Topic topic = new Topic();
    topic.setId(1L);

    when(
        topicRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(topic));

    Optional<Topic> result =
        topicService.findById(1L);

    assertThat(result).isPresent();
    assertThat(result.get()).isSameAs(topic);

    verify(
        topicRepository,
        times(1)
    ).findByIdAndDeletedAtIsNull(1L);
}

@Test
@DisplayName("TopicをRepositoryへ保存できること")
void save_ShouldSaveTopic() {

    Topic topic = new Topic();

    when(
        topicRepository.save(topic)
    ).thenReturn(topic);

    Topic result =
        topicService.save(topic);

    assertThat(result).isSameAs(topic);

    verify(
        topicRepository,
        times(1)
    ).save(topic);
}
}