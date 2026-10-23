package com.example.bulletinboard.service;
import com.example.bulletinboard.security.ActorIds;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.content.AdminAnswerResponse;
import com.example.bulletinboard.dto.content.AdminContentVisibility;
import com.example.bulletinboard.dto.content.AdminTopicResponse;
import com.example.bulletinboard.exception.AdminContentNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

/** 管理用の取得と、既存のTopic/Answer削除処理を使う管理対応。 */
@Service
public class AdminContentService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final TopicRepository topics;
    private final AnswerRepository answers;
    private final TopicImageRepository images;
    private final UserRepository users;
    private final AdminOperationLogRepository logs;
    private final TopicService topicService;
    private final AnswerService answerService;

    public AdminContentService(TopicRepository topics, AnswerRepository answers, TopicImageRepository images,
            UserRepository users, AdminOperationLogRepository logs,
            TopicService topicService, AnswerService answerService) {
        this.topics = topics;
        this.answers = answers;
        this.images = images;
        this.users = users;
        this.logs = logs;
        this.topicService = topicService;
        this.answerService = answerService;
    }

    private User admin(String actorId) {
        if (actorId == null || actorId.isBlank()) throw new UserNotFoundException("ログインユーザー情報を取得できませんでした。");
        User actor = users.findById(ActorIds.parse(actorId))
                .orElseThrow(() -> new UserNotFoundException("ログインユーザー情報を取得できませんでした。"));
        if (!"ROLE_ADMIN".equals(actor.getRole()) || actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("この操作は管理者のみ実行できます。");
        }
        return actor;
    }

    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("一覧条件を確認してください。");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Transactional(readOnly = true)
    public Page<AdminTopicResponse> topics(int page, int size, AdminContentVisibility visibility, String actorId) {
        admin(actorId);
        PageRequest pageable = page(page, size);
        Page<Topic> found = switch (visibility) {
            case PUBLIC -> topics.findByDeletedAtIsNull(pageable);
            case DELETED -> topics.findByDeletedAtIsNotNull(pageable);
            case null -> topics.findAll(pageable);
        };
        return found.map(AdminTopicResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminTopicResponse topic(Long id, String actorId) {
        admin(actorId);
        return AdminTopicResponse.from(topics.findById(id).orElseThrow(AdminContentNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public Page<AdminAnswerResponse> answers(int page, int size, AdminContentVisibility visibility, String actorId) {
        admin(actorId);
        PageRequest pageable = page(page, size);
        Page<Answer> found = switch (visibility) {
            case PUBLIC -> answers.findPublic(pageable);
            case DELETED -> answers.findNotPublic(pageable);
            case null -> answers.findAll(pageable);
        };
        return found.map(AdminAnswerResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminAnswerResponse answer(Long id, String actorId) {
        admin(actorId);
        return AdminAnswerResponse.from(answers.findById(id).orElseThrow(AdminContentNotFoundException::new));
    }

    /** 削除済みTopicの画像も、対象Topicと結び付く管理下画像だけを取得する。 */
    @Transactional(readOnly = true)
    public TopicImage topicImage(Long id, String actorId) {
        admin(actorId);
        Topic topic = topics.findById(id).orElseThrow(AdminContentNotFoundException::new);
        String url = topic.getImage();
        if (url == null || !url.startsWith(TopicImageService.URL_PREFIX)) {
            throw new AdminContentNotFoundException();
        }
        String imageId = url.substring(TopicImageService.URL_PREFIX.length());
        return images.findById(imageId).orElseThrow(AdminContentNotFoundException::new);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteTopic(Long id, String actorId) {
        User actor = admin(actorId);
        // 既存Serviceが行ロックと論理削除を行う。履歴も同じトランザクションに置く。
        topicService.deleteById(id, actorId, true);
        logs.saveAndFlush(new AdminOperationLog(actor, "TOPIC", id,
                "DELETE", "PUBLIC", "DELETED"));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteAnswer(Long id, String actorId) {
        User actor = admin(actorId);
        Long parent = answers.findTopicIdIncludingDeleted(id).orElseThrow(AdminContentNotFoundException::new);
        var lockedTopic = TopicWriteLocks.lock(entityManager, parent, false);
        Answer answer = answers.findById(id).orElseThrow(AdminContentNotFoundException::new);
        String before = lockedTopic.getDeletedAt() == null ? "PUBLIC" : "PARENT_DELETED";
        answerService.deleteAnswer(id, actorId, true);
        logs.saveAndFlush(new AdminOperationLog(actor, "ANSWER", id,
                "DELETE", before, "DELETED"));
    }
}
