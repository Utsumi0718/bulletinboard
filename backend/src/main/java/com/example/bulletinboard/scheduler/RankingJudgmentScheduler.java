package com.example.bulletinboard.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.service.RankingService;

/**
 * 【クラスの役割】
 * ランキングの自動判定を実行するSchedulerです。
 *
 * 一定間隔で判定対象Topicを確認し、
 * DAY_7 / DAY_14 / DAY_21 の未判定Topicについて
 * RankingServiceへランキング判定を依頼します。
 *
 * 実際の王者判定やRankingResult、
 * WinnerAchievementの保存処理は
 * RankingServiceが担当します。
 */
@Component
public class RankingJudgmentScheduler {

    private final TopicRepository topicRepository;
    private final RankingService rankingService;

    public RankingJudgmentScheduler(
            TopicRepository topicRepository,
            RankingService rankingService
    ) {
        this.topicRepository = topicRepository;
        this.rankingService = rankingService;
    }

    /**
     * 1分ごとにランキング判定対象Topicを確認します。
     */
    @Scheduled(cron = "0 * * * * *")
    public void runRankingJudgment() {

        LocalDateTime now = LocalDateTime.now();

        processDueRankings(now);
    }

    /**
     * 現在時刻を基準として、
     * DAY_7 → DAY_14 → DAY_21 の順に
     * 未判定checkpointを処理します。
     */
    void processDueRankings(LocalDateTime now) {

        processCheckpoint(
                now.minusDays(7),
                RankingCheckpoint.DAY_7
        );

        processCheckpoint(
                now.minusDays(14),
                RankingCheckpoint.DAY_14
        );

        processCheckpoint(
                now.minusDays(21),
                RankingCheckpoint.DAY_21
        );
    }

    /**
     * 指定されたcheckpointについて、
     * 判定対象Topicを取得してランキング判定を実行します。
     */
    private void processCheckpoint(
            LocalDateTime threshold,
            RankingCheckpoint checkpoint
    ) {

        List<Topic> topics =
                topicRepository.findTopicsDueForRanking(
                        threshold,
                        checkpoint
                );

        for (Topic topic : topics) {

            rankingService.judgeRanking(
                    topic.getId(),
                    checkpoint
            );
        }
    }
}