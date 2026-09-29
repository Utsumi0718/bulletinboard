package com.example.bulletinboard.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.user.AdminUserActivityResponse;
import com.example.bulletinboard.dto.user.AdminUserResponse;
import com.example.bulletinboard.exception.AdminUserNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

/** User管理の読み取りと凍結・解除を担当する。 */
@Service
public class AdminUserService {
    private final UserRepository users;
    private final TopicRepository topics;
    private final AnswerRepository answers;
    private final AdminOperationLogRepository logs;

    public AdminUserService(UserRepository users, TopicRepository topics, AnswerRepository answers,
            AdminOperationLogRepository logs) {
        this.users = users;
        this.topics = topics;
        this.answers = answers;
        this.logs = logs;
    }

    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("一覧条件を確認してください。");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private User admin(String email) {
        if (email == null || email.isBlank()) throw new UserNotFoundException("ログインユーザー情報を取得できませんでした。");
        User user = users.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("ログインユーザー情報を取得できませんでした。"));
        if (!"ROLE_ADMIN".equals(user.getRole()) || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("この操作は管理者のみ実行できます。");
        }
        return user;
    }

    private User target(Long id) {
        return users.findById(id).orElseThrow(AdminUserNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(int page, int size, String adminEmail) {
        admin(adminEmail);
        return users.findAll(page(page, size)).map(AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse detail(Long id, String adminEmail) {
        admin(adminEmail);
        return AdminUserResponse.from(target(id));
    }

    @Transactional(readOnly = true)
    public Page<AdminUserActivityResponse> topics(Long id, int page, int size, String adminEmail) {
        admin(adminEmail);
        target(id);
        return topics.findByUserId(id, page(page, size)).map(AdminUserActivityResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserActivityResponse> answers(Long id, int page, int size, String adminEmail) {
        admin(adminEmail);
        target(id);
        return answers.findByUserId(id, page(page, size)).map(AdminUserActivityResponse::from);
    }

    @Transactional
    public AdminUserResponse setStatus(Long id, AccountStatus next, String adminEmail) {
        if (next != AccountStatus.ACTIVE && next != AccountStatus.FROZEN) {
            throw new IllegalArgumentException("指定された状態には変更できません。");
        }
        User actor = admin(adminEmail);
        User user = users.findByIdForUpdate(id).orElseThrow(AdminUserNotFoundException::new);
        if (actor.getId().equals(user.getId()) || "ROLE_ADMIN".equals(user.getRole()) ||
                user.getAccountStatus() == AccountStatus.WITHDRAWN) {
            throw new ForbiddenOperationException("このユーザーの状態は変更できません。");
        }
        AccountStatus before = user.getAccountStatus();
        if (before != next) {
            user.setAccountStatus(next);
            users.saveAndFlush(user);
            logs.saveAndFlush(new AdminOperationLog(actor, "USER", id,
                    "STATUS_CHANGE", before.name(), next.name()));
        }
        return AdminUserResponse.from(user);
    }
}
