package com.example.bulletinboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.RankingJudgment;
import com.example.bulletinboard.model.RankingResult;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.model.WinnerAchievement;
import com.example.bulletinboard.repository.AnswerLikeCount;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.LikeRepository;
import com.example.bulletinboard.repository.RankingJudgmentRepository;
import com.example.bulletinboard.repository.RankingResultRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.WinnerAchievementRepository;




@ExtendWith(MockitoExtension.class)
public class RankingServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RankingJudgmentRepository rankingJudgmentRepository;

    @Mock
    private RankingResultRepository rankingResultRepository;

    @Mock
    private WinnerAchievementRepository winnerAchievementRepository;

    @InjectMocks
    private RankingService rankingService;


    @Test
    @DisplayName("topicが存在しない場合は何も保存しない")
    void shouldNotSaveAnythingWhenTopicDoesNotExist() {

    Long topicId = 1L;

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    verify(topicRepository).findById(topicId);

    verifyNoInteractions(
            answerRepository,
            likeRepository,
            rankingJudgmentRepository,
            rankingResultRepository,
            winnerAchievementRepository
    );
}

@Test
@DisplayName("論理削除済みTopicはランキング判定を行わない")
void shouldNotJudgeRankingWhenTopicIsDeleted() {

    Long topicId = 1L;

    Topic topic = new Topic();
    topic.setDeletedAt(LocalDateTime.now());

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    verify(topicRepository).findById(topicId);

    verifyNoInteractions(
            answerRepository,
            likeRepository,
            rankingJudgmentRepository,
            rankingResultRepository,
            winnerAchievementRepository
    );
}

@Test
@DisplayName("同じTopic × checkpointがすでに判定済みなら二重判定しない")
void shouldNotJudgeRankingWhenAlreadyJudged() {

    Long topicId = 1L;

    Topic topic = new Topic();
    RankingJudgment existingJudgment = new RankingJudgment();

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.of(existingJudgment));

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    verify(topicRepository).findById(topicId);

    verify(rankingJudgmentRepository)
            .findByTopicAndCheckpoint(
                    topic,
                    RankingCheckpoint.DAY_7
            );

    verifyNoMoreInteractions(rankingJudgmentRepository);

    verifyNoInteractions(
            answerRepository,
            likeRepository,
            rankingResultRepository,
            winnerAchievementRepository
    );
}

@Test
@DisplayName("Answerが0件の場合はRankingJudgmentのみ保存する")
void shouldSaveOnlyRankingJudgmentWhenNoAnswersExist() {

    Long topicId = 1L;

    Topic topic = new Topic();

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<RankingJudgment> judgmentCaptor =
            ArgumentCaptor.forClass(RankingJudgment.class);

    verify(rankingJudgmentRepository)
            .save(judgmentCaptor.capture());

    RankingJudgment savedJudgment =
            judgmentCaptor.getValue();

    assertEquals(topic, savedJudgment.getTopic());
    assertEquals(
            RankingCheckpoint.DAY_7,
            savedJudgment.getCheckpoint()
    );
    assertNotNull(savedJudgment.getJudgedAt());

    verifyNoInteractions(
            likeRepository,
            rankingResultRepository,
            winnerAchievementRepository
    );
}

@Test
@DisplayName("全Answerが0Likeの場合はRankingJudgmentのみ保存する")
void shouldSaveOnlyRankingJudgmentWhenAllAnswersHaveZeroLikes() {

    Long topicId = 1L;

    Topic topic = new Topic();

    Answer answer1 = new Answer();
    answer1.setId(10L);

    Answer answer2 = new Answer();
    answer2.setId(20L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer1, answer2));

    // Likeが1件もないため、集計結果も空
    when(likeRepository.countLikesByAnswerIds(
            List.of(10L, 20L)
    ))
            .thenReturn(List.of());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<RankingJudgment> judgmentCaptor =
            ArgumentCaptor.forClass(RankingJudgment.class);

    verify(rankingJudgmentRepository)
            .save(judgmentCaptor.capture());

    RankingJudgment savedJudgment =
            judgmentCaptor.getValue();

    assertEquals(topic, savedJudgment.getTopic());
    assertEquals(
            RankingCheckpoint.DAY_7,
            savedJudgment.getCheckpoint()
    );
    assertNotNull(savedJudgment.getJudgedAt());

    verify(likeRepository)
            .countLikesByAnswerIds(
                    List.of(10L, 20L)
            );

    verifyNoInteractions(
            rankingResultRepository,
            winnerAchievementRepository
    );
}

@Test
@DisplayName("単独1位の場合はRankingResultと初回WinnerAchievementを保存する")
void shouldSaveRankingResultAndAchievementWhenThereIsSingleWinner() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    Answer answer = new Answer();
    answer.setId(10L);
    answer.setUser(user);

    AnswerLikeCount likeCount = mock(AnswerLikeCount.class);

    when(likeCount.getAnswerId())
            .thenReturn(10L);

    when(likeCount.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L)
    ))
            .thenReturn(List.of(likeCount));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<RankingResult> resultCaptor =
            ArgumentCaptor.forClass(RankingResult.class);

    verify(rankingResultRepository)
            .save(resultCaptor.capture());

    RankingResult savedResult = resultCaptor.getValue();

    assertEquals(answer, savedResult.getAnswer());
    assertEquals(5, savedResult.getLikeCount());
    assertNotNull(savedResult.getRankingJudgment());

    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository)
            .save(achievementCaptor.capture());

    WinnerAchievement savedAchievement =
            achievementCaptor.getValue();

    assertEquals(user, savedAchievement.getUser());
    assertEquals(topic, savedAchievement.getTopic());
    assertEquals(answer, savedAchievement.getAnswer());
    assertEquals(5, savedAchievement.getLikeCount());
    assertNotNull(savedAchievement.getAchievedAt());
}

@Test
@DisplayName("複数Answerが同率1位の場合はすべてRankingResultに保存する")
void shouldSaveAllRankingResultsWhenMultipleAnswersTieForFirstPlace() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user1 = new User();
    user1.setId(100L);

    User user2 = new User();
    user2.setId(200L);

    Answer answer1 = new Answer();
    answer1.setId(10L);
    answer1.setUser(user1);

    Answer answer2 = new Answer();
    answer2.setId(20L);
    answer2.setUser(user2);

    AnswerLikeCount likeCount1 = mock(AnswerLikeCount.class);
    AnswerLikeCount likeCount2 = mock(AnswerLikeCount.class);

    when(likeCount1.getAnswerId())
            .thenReturn(10L);
    when(likeCount1.getLikeCount())
            .thenReturn(5L);

    when(likeCount2.getAnswerId())
            .thenReturn(20L);
    when(likeCount2.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer1, answer2));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L, 20L)
    ))
            .thenReturn(List.of(likeCount1, likeCount2));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(user1, topic))
            .thenReturn(Optional.empty());

    when(winnerAchievementRepository.findByUserAndTopic(user2, topic))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<RankingResult> resultCaptor =
            ArgumentCaptor.forClass(RankingResult.class);

    verify(rankingResultRepository, times(2))
            .save(resultCaptor.capture());

    List<RankingResult> savedResults =
            resultCaptor.getAllValues();

    assertEquals(2, savedResults.size());

    assertEquals(answer1, savedResults.get(0).getAnswer());
    assertEquals(5, savedResults.get(0).getLikeCount());

    assertEquals(answer2, savedResults.get(1).getAnswer());
    assertEquals(5, savedResults.get(1).getLikeCount());
}


@Test
@DisplayName("同率1位が複数Userの場合は各UserにWinnerAchievementを保存する")
void shouldSaveAchievementForEachUserWhenMultipleUsersTieForFirstPlace() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user1 = new User();
    user1.setId(100L);

    User user2 = new User();
    user2.setId(200L);

    Answer answer1 = new Answer();
    answer1.setId(10L);
    answer1.setUser(user1);

    Answer answer2 = new Answer();
    answer2.setId(20L);
    answer2.setUser(user2);

    AnswerLikeCount likeCount1 = mock(AnswerLikeCount.class);
    AnswerLikeCount likeCount2 = mock(AnswerLikeCount.class);

    when(likeCount1.getAnswerId())
            .thenReturn(10L);
    when(likeCount1.getLikeCount())
            .thenReturn(5L);

    when(likeCount2.getAnswerId())
            .thenReturn(20L);
    when(likeCount2.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer1, answer2));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L, 20L)
    ))
            .thenReturn(List.of(likeCount1, likeCount2));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(user1, topic))
            .thenReturn(Optional.empty());

    when(winnerAchievementRepository.findByUserAndTopic(user2, topic))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository, times(2))
            .save(achievementCaptor.capture());

    List<WinnerAchievement> savedAchievements =
            achievementCaptor.getAllValues();

    assertEquals(2, savedAchievements.size());

    assertEquals(user1, savedAchievements.get(0).getUser());
    assertEquals(topic, savedAchievements.get(0).getTopic());
    assertEquals(answer1, savedAchievements.get(0).getAnswer());
    assertEquals(5, savedAchievements.get(0).getLikeCount());
    assertNotNull(savedAchievements.get(0).getAchievedAt());

    assertEquals(user2, savedAchievements.get(1).getUser());
    assertEquals(topic, savedAchievements.get(1).getTopic());
    assertEquals(answer2, savedAchievements.get(1).getAnswer());
    assertEquals(5, savedAchievements.get(1).getLikeCount());
    assertNotNull(savedAchievements.get(1).getAchievedAt());
}

@Test
@DisplayName("同一Userの複数Answerが同率1位の場合はRankingResultを全件保存しAchievementは1件にする")
void shouldSaveAllRankingResultsButOnlyOneAchievementWhenSameUserHasMultipleWinningAnswers() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    Answer answer1 = new Answer();
    answer1.setId(10L);
    answer1.setUser(user);
    answer1.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));

    Answer answer2 = new Answer();
    answer2.setId(20L);
    answer2.setUser(user);
    answer2.setCreatedAt(LocalDateTime.of(2026, 9, 1, 11, 0));

    AnswerLikeCount likeCount1 = mock(AnswerLikeCount.class);
    AnswerLikeCount likeCount2 = mock(AnswerLikeCount.class);

    when(likeCount1.getAnswerId())
            .thenReturn(10L);
    when(likeCount1.getLikeCount())
            .thenReturn(5L);

    when(likeCount2.getAnswerId())
            .thenReturn(20L);
    when(likeCount2.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer1, answer2));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L, 20L)
    ))
            .thenReturn(List.of(likeCount1, likeCount2));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    // 王者Answerは2件ともRankingResultへ保存される
    ArgumentCaptor<RankingResult> resultCaptor =
            ArgumentCaptor.forClass(RankingResult.class);

    verify(rankingResultRepository, times(2))
            .save(resultCaptor.capture());

    List<RankingResult> savedResults =
            resultCaptor.getAllValues();

    assertEquals(2, savedResults.size());
    assertEquals(answer1, savedResults.get(0).getAnswer());
    assertEquals(answer2, savedResults.get(1).getAnswer());

    // 同じUser × TopicなのでAchievementは1件だけ
    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository, times(1))
            .save(achievementCaptor.capture());

    WinnerAchievement savedAchievement =
            achievementCaptor.getValue();

    assertEquals(user, savedAchievement.getUser());
    assertEquals(topic, savedAchievement.getTopic());

    // より早く投稿されたAnswerを代表として保存する
    assertEquals(answer1, savedAchievement.getAnswer());
    assertEquals(5, savedAchievement.getLikeCount());
    assertNotNull(savedAchievement.getAchievedAt());
}

@Test
@DisplayName("同一Userの王者AnswerのcreatedAtが同じ場合はIDが小さいAnswerを代表にする")
void shouldSelectSmallerAnswerIdWhenWinningAnswersHaveSameCreatedAt() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    LocalDateTime sameCreatedAt =
            LocalDateTime.of(2026, 9, 1, 10, 0);

    Answer answer1 = new Answer();
    answer1.setId(10L);
    answer1.setUser(user);
    answer1.setCreatedAt(sameCreatedAt);

    Answer answer2 = new Answer();
    answer2.setId(20L);
    answer2.setUser(user);
    answer2.setCreatedAt(sameCreatedAt);

    AnswerLikeCount likeCount1 = mock(AnswerLikeCount.class);
    AnswerLikeCount likeCount2 = mock(AnswerLikeCount.class);

    when(likeCount1.getAnswerId())
            .thenReturn(10L);
    when(likeCount1.getLikeCount())
            .thenReturn(5L);

    when(likeCount2.getAnswerId())
            .thenReturn(20L);
    when(likeCount2.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer1, answer2));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L, 20L)
    ))
            .thenReturn(List.of(likeCount1, likeCount2));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_7
    );

    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository)
            .save(achievementCaptor.capture());

    WinnerAchievement savedAchievement =
            achievementCaptor.getValue();

    assertEquals(answer1, savedAchievement.getAnswer());
}

@Test
@DisplayName("WinnerAchievementが既に存在する場合は新規作成せず初回実績を保持する")
void shouldNotCreateOrUpdateAchievementWhenAchievementAlreadyExists() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    // DAY_7で初王者になったときのAnswer
    Answer firstWinningAnswer = new Answer();
    firstWinningAnswer.setId(10L);
    firstWinningAnswer.setUser(user);

    LocalDateTime firstAchievedAt =
            LocalDateTime.of(2026, 9, 8, 10, 0);

    WinnerAchievement existingAchievement =
            new WinnerAchievement();

    existingAchievement.setUser(user);
    existingAchievement.setTopic(topic);
    existingAchievement.setAnswer(firstWinningAnswer);
    existingAchievement.setLikeCount(3);
    existingAchievement.setAchievedAt(firstAchievedAt);

    // DAY_14で新しく王者になったAnswer
    Answer currentWinningAnswer = new Answer();
    currentWinningAnswer.setId(20L);
    currentWinningAnswer.setUser(user);

    AnswerLikeCount likeCount = mock(AnswerLikeCount.class);

    when(likeCount.getAnswerId())
            .thenReturn(20L);

    when(likeCount.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_14
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(currentWinningAnswer));

    when(likeRepository.countLikesByAnswerIds(
            List.of(20L)
    ))
            .thenReturn(List.of(likeCount));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.of(existingAchievement));

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_14
    );

    // DAY_14の王者AnswerはRankingResultとして保存する
    ArgumentCaptor<RankingResult> resultCaptor =
            ArgumentCaptor.forClass(RankingResult.class);

    verify(rankingResultRepository)
            .save(resultCaptor.capture());

    RankingResult savedResult =
            resultCaptor.getValue();

    assertEquals(currentWinningAnswer, savedResult.getAnswer());
    assertEquals(5, savedResult.getLikeCount());

    // Achievementはすでに存在するので新規保存しない
    verify(winnerAchievementRepository, never())
            .save(any(WinnerAchievement.class));

    // 初回王者時のAchievementは変更されない
    assertEquals(firstWinningAnswer, existingAchievement.getAnswer());
    assertEquals(3, existingAchievement.getLikeCount());
    assertEquals(firstAchievedAt, existingAchievement.getAchievedAt());
}


@Test
@DisplayName("DAY_14で初めて王者になった場合はWinnerAchievementを保存する")
void shouldCreateAchievementWhenUserFirstWinsAtDay14() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    Answer answer = new Answer();
    answer.setId(10L);
    answer.setUser(user);

    AnswerLikeCount likeCount = mock(AnswerLikeCount.class);

    when(likeCount.getAnswerId())
            .thenReturn(10L);

    when(likeCount.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_14
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L)
    ))
            .thenReturn(List.of(likeCount));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    // このTopicではまだ一度も王者実績を持っていない
    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_14
    );

    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository)
            .save(achievementCaptor.capture());

    WinnerAchievement savedAchievement =
            achievementCaptor.getValue();

    assertEquals(user, savedAchievement.getUser());
    assertEquals(topic, savedAchievement.getTopic());
    assertEquals(answer, savedAchievement.getAnswer());
    assertEquals(5, savedAchievement.getLikeCount());
    assertNotNull(savedAchievement.getAchievedAt());
}

@Test
@DisplayName("DAY_21で初めて王者になった場合はWinnerAchievementを保存する")
void shouldCreateAchievementWhenUserFirstWinsAtDay21() {

    Long topicId = 1L;

    Topic topic = new Topic();

    User user = new User();
    user.setId(100L);

    Answer answer = new Answer();
    answer.setId(10L);
    answer.setUser(user);

    AnswerLikeCount likeCount = mock(AnswerLikeCount.class);

    when(likeCount.getAnswerId())
            .thenReturn(10L);

    when(likeCount.getLikeCount())
            .thenReturn(5L);

    when(topicRepository.findById(topicId))
            .thenReturn(Optional.of(topic));

    when(rankingJudgmentRepository.findByTopicAndCheckpoint(
            topic,
            RankingCheckpoint.DAY_21
    ))
            .thenReturn(Optional.empty());

    when(answerRepository.findByTopicIdAndDeletedAtIsNull(topicId))
            .thenReturn(List.of(answer));

    when(likeRepository.countLikesByAnswerIds(
            List.of(10L)
    ))
            .thenReturn(List.of(likeCount));

    when(rankingJudgmentRepository.save(any(RankingJudgment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    when(winnerAchievementRepository.findByUserAndTopic(
            user,
            topic
    ))
            .thenReturn(Optional.empty());

    rankingService.judgeRanking(
            topicId,
            RankingCheckpoint.DAY_21
    );

    ArgumentCaptor<WinnerAchievement> achievementCaptor =
            ArgumentCaptor.forClass(WinnerAchievement.class);

    verify(winnerAchievementRepository)
            .save(achievementCaptor.capture());

    WinnerAchievement savedAchievement =
            achievementCaptor.getValue();

    assertEquals(user, savedAchievement.getUser());
    assertEquals(topic, savedAchievement.getTopic());
    assertEquals(answer, savedAchievement.getAnswer());
    assertEquals(5, savedAchievement.getLikeCount());
    assertNotNull(savedAchievement.getAchievedAt());
}


}
