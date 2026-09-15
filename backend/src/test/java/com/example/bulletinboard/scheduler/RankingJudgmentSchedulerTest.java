package com.example.bulletinboard.scheduler;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.service.RankingService;

/**
 * 【クラスの役割】
 * RankingJudgmentSchedulerの自動ランキング判定処理を
 * 単体テストするクラスです。
 *
 * 固定した現在時刻を基準として、
 * DAY_7 / DAY_14 / DAY_21それぞれの
 * 判定対象Topicが正しく取得されることを確認します。
 *
 * また、判定対象Topicが存在する場合に、
 * RankingService.judgeRanking()へ
 * 正しいTopic IDとcheckpointを渡して
 * ランキング判定を依頼することを確認します。
 */

@ExtendWith(MockitoExtension.class)
public class RankingJudgmentSchedulerTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private RankingService rankingService;

    @InjectMocks
    private RankingJudgmentScheduler rankingJudgmentScheduler;

    @Test
    @DisplayName("現在時刻を基準にDAY_7・DAY_14・DAY_21の判定対象を取得する")
    void shouldFindDueTopicsForEachCheckpoint() {

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 15, 12, 0);

        LocalDateTime day7Threshold =
                LocalDateTime.of(2026, 9, 8, 12, 0);

        LocalDateTime day14Threshold =
                LocalDateTime.of(2026, 9, 1, 12, 0);

        LocalDateTime day21Threshold =
                LocalDateTime.of(2026, 8, 25, 12, 0);

        when(topicRepository.findTopicsDueForRanking(
                day7Threshold,
                RankingCheckpoint.DAY_7
        ))
                .thenReturn(List.of());

        when(topicRepository.findTopicsDueForRanking(
                day14Threshold,
                RankingCheckpoint.DAY_14
        ))
                .thenReturn(List.of());

        when(topicRepository.findTopicsDueForRanking(
                day21Threshold,
                RankingCheckpoint.DAY_21
        ))
                .thenReturn(List.of());

        rankingJudgmentScheduler.processDueRankings(now);

        verify(topicRepository).findTopicsDueForRanking(
                day7Threshold,
                RankingCheckpoint.DAY_7
        );

        verify(topicRepository).findTopicsDueForRanking(
                day14Threshold,
                RankingCheckpoint.DAY_14
        );

        verify(topicRepository).findTopicsDueForRanking(
                day21Threshold,
                RankingCheckpoint.DAY_21
        );

        verifyNoInteractions(rankingService);
    }

    @Test
    @DisplayName("判定対象Topicが存在する場合はRankingServiceにランキング判定を依頼する")
    void shouldJudgeRankingWhenDueTopicExists() {

    LocalDateTime now =
            LocalDateTime.of(2026, 9, 15, 12, 0);

    LocalDateTime day7Threshold =
            now.minusDays(7);

    LocalDateTime day14Threshold =
            now.minusDays(14);

    LocalDateTime day21Threshold =
            now.minusDays(21);

    Topic topic = new Topic();
    topic.setId(1L);

    when(topicRepository.findTopicsDueForRanking(
            day7Threshold,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(List.of(topic));

    when(topicRepository.findTopicsDueForRanking(
            day14Threshold,
            RankingCheckpoint.DAY_14
    ))
            .thenReturn(List.of());

    when(topicRepository.findTopicsDueForRanking(
            day21Threshold,
            RankingCheckpoint.DAY_21
    ))
            .thenReturn(List.of());

    rankingJudgmentScheduler.processDueRankings(now);

    verify(rankingService).judgeRanking(
            1L,
            RankingCheckpoint.DAY_7
    );
}

@Test
@DisplayName("同じcheckpointに複数の判定対象Topicがある場合はすべて判定する")
void shouldJudgeAllDueTopicsForSameCheckpoint() {

    LocalDateTime now =
            LocalDateTime.of(2026, 9, 15, 12, 0);

    LocalDateTime day7Threshold =
            now.minusDays(7);

    LocalDateTime day14Threshold =
            now.minusDays(14);

    LocalDateTime day21Threshold =
            now.minusDays(21);

    Topic topic1 = new Topic();
    topic1.setId(1L);

    Topic topic2 = new Topic();
    topic2.setId(2L);

    when(topicRepository.findTopicsDueForRanking(
            day7Threshold,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(List.of(topic1, topic2));

    when(topicRepository.findTopicsDueForRanking(
            day14Threshold,
            RankingCheckpoint.DAY_14
    ))
            .thenReturn(List.of());

    when(topicRepository.findTopicsDueForRanking(
            day21Threshold,
            RankingCheckpoint.DAY_21
    ))
            .thenReturn(List.of());

    rankingJudgmentScheduler.processDueRankings(now);

    verify(rankingService).judgeRanking(
            1L,
            RankingCheckpoint.DAY_7
    );

    verify(rankingService).judgeRanking(
            2L,
            RankingCheckpoint.DAY_7
    );
}

@Test
@DisplayName("複数checkpointが未判定の場合はDAY_7からDAY_21の順に補完する")
void shouldProcessOverdueCheckpointsInOrder() {

    LocalDateTime now =
            LocalDateTime.of(2026, 9, 30, 12, 0);

    LocalDateTime day7Threshold =
            now.minusDays(7);

    LocalDateTime day14Threshold =
            now.minusDays(14);

    LocalDateTime day21Threshold =
            now.minusDays(21);

    Topic topic = new Topic();
    topic.setId(1L);

    when(topicRepository.findTopicsDueForRanking(
            day7Threshold,
            RankingCheckpoint.DAY_7
    ))
            .thenReturn(List.of(topic));

    when(topicRepository.findTopicsDueForRanking(
            day14Threshold,
            RankingCheckpoint.DAY_14
    ))
            .thenReturn(List.of(topic));

    when(topicRepository.findTopicsDueForRanking(
            day21Threshold,
            RankingCheckpoint.DAY_21
    ))
            .thenReturn(List.of(topic));

    rankingJudgmentScheduler.processDueRankings(now);

    InOrder inOrder = inOrder(rankingService);

    inOrder.verify(rankingService).judgeRanking(
            1L,
            RankingCheckpoint.DAY_7
    );

    inOrder.verify(rankingService).judgeRanking(
            1L,
            RankingCheckpoint.DAY_14
    );

    inOrder.verify(rankingService).judgeRanking(
            1L,
            RankingCheckpoint.DAY_21
    );
}
}