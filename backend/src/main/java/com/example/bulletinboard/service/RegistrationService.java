package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.auth.RegisterRequest;
import com.example.bulletinboard.dto.auth.RegisterResponse;
import com.example.bulletinboard.exception.RegistrationConflictException;
import com.example.bulletinboard.exception.RegistrationConflictException.Field;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

/** Userと初期Profileを同じトランザクションで登録する。 */
@Service
public class RegistrationService {
    private final UserRepository users;
    private final CustomUserDetailsService userDetailsService;

    public RegistrationService(UserRepository users, CustomUserDetailsService userDetailsService) {
        this.users = users;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new RegistrationConflictException(Field.USERNAME);
        }
        if (users.existsByEmail(request.email())) {
            throw new RegistrationConflictException(Field.EMAIL);
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());
        // D-4で整備したUser・Profile同時保存とパスワードハッシュ化を再利用する。
        userDetailsService.registerUser(user);

        return new RegisterResponse(user.getId(), user.getUsername());
    }
}
