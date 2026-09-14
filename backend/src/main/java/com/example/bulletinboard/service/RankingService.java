package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.RankingJudgment;
import com.example.bulletinboard.model.RankingResult;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.WinnerAchievement;
import com.example.bulletinboard.repository.AnswerLikeCount;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.LikeRepository;
import com.example.bulletinboard.repository.RankingJudgmentRepository;
import com.example.bulletinboard.repository.RankingResultRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.WinnerAchievementRepository;

/**
 * Topicごとのランキング判定を担当するService。
 *
 * <p>ランキング対象のAnswerとLike数をもとに王者を判定し、
 * RankingJudgment、RankingResult、WinnerAchievementを保存する。</p>
 *
 * <p>同一Topic・同一checkpointの二重判定防止、
 * 同率1位の保存、Userごとの初回王者実績の保存も担当する。</p>
 */
@Service
public class RankingService {

    private final TopicRepository topicRepository;
    private final AnswerRepository answerRepository;
    private final LikeRepository likeRepository;
    private final RankingJudgmentRepository rankingJudgmentRepository;
    private final RankingResultRepository rankingResultRepository;
    private final WinnerAchievementRepository winnerAchievementRepository;

    public RankingService(
            TopicRepository topicRepository,
            AnswerRepository answerRepository,
            LikeRepository likeRepository,
            RankingJudgmentRepository rankingJudgmentRepository,
            RankingResultRepository rankingResultRepository,
            WinnerAchievementRepository winnerAchievementRepository) {

        this.topicRepository = topicRepository;
        this.answerRepository = answerRepository;
        this.likeRepository = likeRepository;
        this.rankingJudgmentRepository = rankingJudgmentRepository;
        this.rankingResultRepository = rankingResultRepository;
        this.winnerAchievementRepository = winnerAchievementRepository;
    }

    /**
     * 指定されたTopicとcheckpointについてランキング判定を実行する。
     *
     * <p>Topicの存在・論理削除・判定済み状態を確認した後、
     * 有効なAnswerのLike数を比較して王者を決定する。</p>
     *
     * <p>Answerが存在しない場合、または全AnswerのLike数が0の場合は、
     * RankingJudgmentのみを保存する。</p>
     *
     * @param topicId 判定対象TopicのID
     * @param checkpoint 判定対象のチェックポイント
     */
    @Transactional
    public void judgeRanking(Long topicId, RankingCheckpoint checkpoint) {

        Optional<Topic> topic = topicRepository.findById(topicId);

        // Topicが存在しない場合は処理を終了する
        if (topic.isEmpty()) {
            return;
        }

        Topic targetTopic = topic.get();

        // 論理削除済みTopicはランキング判定を行わない
        if (targetTopic.getDeletedAt() != null) {
            return;
        }

        // このTopic × checkpointが判定済みか確認する
        Optional<RankingJudgment> existingJudgment =
                rankingJudgmentRepository.findByTopicAndCheckpoint(
                        targetTopic,
                        checkpoint
                );

        if (existingJudgment.isPresent()) {
            return;
        }

        // ランキング対象となる有効なAnswerを取得する
        List<Answer> targetAnswers =
                answerRepository.findByTopicIdAndDeletedAtIsNull(topicId);

        RankingJudgment judgment =
                createRankingJudgment(targetTopic, checkpoint);

        // Answerが存在しなくても判定を実行した事実は保存する
        if (targetAnswers.isEmpty()) {
            rankingJudgmentRepository.save(judgment);
            return;
        }

        List<Long> answerIds = targetAnswers.stream()
                .map(Answer::getId)
                .toList();

        List<AnswerLikeCount> likeCounts =
                likeRepository.countLikesByAnswerIds(answerIds);

        Map<Long, Long> likeCountMap =
                createLikeCountMap(likeCounts);

        long maxLikeCount =
                getMaxLikeCount(targetAnswers, likeCountMap);

        // 全Answerが0Likeの場合は王者なしとして判定記録のみ保存する
        if (maxLikeCount == 0L) {
            rankingJudgmentRepository.save(judgment);
            return;
        }

        List<Answer> winningAnswers =
                findWinningAnswers(
                        targetAnswers,
                        likeCountMap,
                        maxLikeCount
                );

        // ランキング判定結果を保存する
        RankingJudgment savedJudgment =
                rankingJudgmentRepository.save(judgment);

        saveRankingResults(
                savedJudgment,
                winningAnswers,
                maxLikeCount
        );

        // UserごとにWinnerAchievementへ保存する代表Answerを決める
        Map<Long, Answer> representativeAnswers =
                selectRepresentativeAnswers(winningAnswers);

        saveWinnerAchievements(
                targetTopic,
                representativeAnswers,
                maxLikeCount,
                savedJudgment.getJudgedAt()
        );
    }

    /**
     * Topicとcheckpointに対応するランキング判定記録を生成する。
     *
     * <p>この時点ではDBへの保存は行わず、
     * 実際に判定を実行した日時をjudgedAtとして設定する。</p>
     *
     * @param targetTopic 判定対象Topic
     * @param checkpoint 判定対象のチェックポイント
     * @return 生成したRankingJudgment
     */
    private RankingJudgment createRankingJudgment(
            Topic targetTopic,
            RankingCheckpoint checkpoint) {

        RankingJudgment judgment = new RankingJudgment();
        judgment.setTopic(targetTopic);
        judgment.setCheckpoint(checkpoint);
        judgment.setJudgedAt(LocalDateTime.now());

        return judgment;
    }

    /**
     * AnswerごとのLike集計結果をMapへ変換する。
     *
     * <p>キーにAnswer ID、値にLike数を保持する。
     * Likeが0件のAnswerはMapに含まれないため、
     * 利用時に0として補完する。</p>
     *
     * @param likeCounts AnswerごとのLike集計結果
     * @return Answer IDとLike数の対応Map
     */
    private Map<Long, Long> createLikeCountMap(
            List<AnswerLikeCount> likeCounts) {

        Map<Long, Long> countMap = likeCounts.stream()
                .collect(Collectors.toMap(
                        AnswerLikeCount::getAnswerId,
                        AnswerLikeCount::getLikeCount
                ));

        return countMap;
    }

    /**
     * ランキング対象Answerの中から最大Like数を取得する。
     *
     * <p>Like集計結果に存在しないAnswerは0Likeとして扱う。</p>
     *
     * @param targetAnswers ランキング対象Answer一覧
     * @param likeCountMap Answer IDとLike数の対応Map
     * @return 最大Like数
     */
    private long getMaxLikeCount(
            List<Answer> targetAnswers,
            Map<Long, Long> likeCountMap) {

        long maxLike = targetAnswers.stream()
                .mapToLong(answer ->
                        likeCountMap.getOrDefault(answer.getId(), 0L))
                .max()
                .orElse(0L);

        return maxLike;
    }

    /**
     * 最大Like数と同じLike数を持つ王者Answerをすべて抽出する。
     *
     * <p>同率1位が存在する場合は、該当するAnswerをすべて返す。</p>
     *
     * @param targetAnswers ランキング対象Answer一覧
     * @param likeCountMap Answer IDとLike数の対応Map
     * @param maxLikeCount 最大Like数
     * @return 王者となったAnswer一覧
     */
    private List<Answer> findWinningAnswers(
            List<Answer> targetAnswers,
            Map<Long, Long> likeCountMap,
            long maxLikeCount) {

        List<Answer> winningAnswer = targetAnswers.stream()
                .filter(answer ->
                        likeCountMap.getOrDefault(
                                answer.getId(),
                                0L
                        ) == maxLikeCount)
                .toList();

        return winningAnswer;
    }

    /**
     * 王者となったAnswerをRankingResultとして保存する。
     *
     * <p>同率1位が複数存在する場合も、
     * 王者となったAnswerごとに1件ずつ保存する。</p>
     *
     * @param savedJudgment 保存済みのランキング判定記録
     * @param winningAnswers 王者となったAnswer一覧
     * @param maxLikeCount 判定時点の最大Like数
     */
    private void saveRankingResults(
            RankingJudgment savedJudgment,
            List<Answer> winningAnswers,
            long maxLikeCount) {

        for (Answer winningAnswer : winningAnswers) {

            RankingResult rankingResult = new RankingResult();
            rankingResult.setRankingJudgment(savedJudgment);
            rankingResult.setAnswer(winningAnswer);
            rankingResult.setLikeCount(Math.toIntExact(maxLikeCount));

            rankingResultRepository.save(rankingResult);
        }
    }

    /**
     * 王者AnswerからUserごとの代表Answerを決定する。
     *
     * <p>同一Userが複数のAnswerで同率1位になった場合は、
     * createdAtが最も早いAnswerを代表とする。</p>
     *
     * <p>createdAtが同じ場合は、IDが最も小さいAnswerを代表とする。</p>
     *
     * @param winningAnswers 王者となったAnswer一覧
     * @return User IDと代表Answerの対応Map
     */
    private Map<Long, Answer> selectRepresentativeAnswers(
            List<Answer> winningAnswers) {

        Map<Long, Answer> representativeAnswers = new HashMap<>();

        for (Answer winningAnswer : winningAnswers) {

            Long userId = winningAnswer.getUser().getId();

            Answer currentRepresentative =
                    representativeAnswers.get(userId);

            // このUserの代表Answerがまだなければ登録する
            if (currentRepresentative == null) {
                representativeAnswers.put(userId, winningAnswer);
                continue;
            }

            // 作成日時がより早いAnswerを優先する
            boolean createdEarlier =
                    winningAnswer.getCreatedAt()
                            .isBefore(currentRepresentative.getCreatedAt());

            // 作成日時が同じ場合はIDが小さいAnswerを優先する
            boolean sameCreatedAtAndSmallerId =
                    winningAnswer.getCreatedAt()
                            .isEqual(currentRepresentative.getCreatedAt())
                    && winningAnswer.getId()
                            < currentRepresentative.getId();

            if (createdEarlier || sameCreatedAtAndSmallerId) {
                representativeAnswers.put(userId, winningAnswer);
            }
        }

        return representativeAnswers;
    }

    /**
     * 初めて王者になったUserのWinnerAchievementを保存する。
     *
     * <p>同じUser × TopicのAchievementがすでに存在する場合は
     * 新規作成や更新を行わず、初回王者時の実績を保持する。</p>
     *
     * @param targetTopic 王者判定対象Topic
     * @param representativeAnswers Userごとの代表Answer
     * @param maxLikeCount 初回王者時点のLike数
     * @param achievedAt 王者実績を獲得した日時
     */
    private void saveWinnerAchievements(
            Topic targetTopic,
            Map<Long, Answer> representativeAnswers,
            long maxLikeCount,
            LocalDateTime achievedAt) {

        for (Answer representativeAnswer : representativeAnswers.values()) {

            Optional<WinnerAchievement> existingAchievement =
                    winnerAchievementRepository.findByUserAndTopic(
                            representativeAnswer.getUser(),
                            targetTopic
                    );

            // すでに実績がある場合は初回実績を維持する
            if (existingAchievement.isPresent()) {
                continue;
            }

            WinnerAchievement achievement = new WinnerAchievement();
            achievement.setUser(representativeAnswer.getUser());
            achievement.setTopic(targetTopic);
            achievement.setAnswer(representativeAnswer);
            achievement.setLikeCount(Math.toIntExact(maxLikeCount));
            achievement.setAchievedAt(achievedAt);

            winnerAchievementRepository.save(achievement);
        }
    }
}