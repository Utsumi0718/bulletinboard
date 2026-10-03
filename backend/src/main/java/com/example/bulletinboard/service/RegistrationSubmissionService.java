package com.example.bulletinboard.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.auth.RegisterRequest;
import com.example.bulletinboard.dto.auth.RegisterResponse;
import com.example.bulletinboard.exception.RegistrationConflictException;
import com.example.bulletinboard.exception.RegistrationConflictException.Field;
import com.example.bulletinboard.exception.RegistrationOperationException;
import com.example.bulletinboard.repository.UserRepository;

/** コミット失敗をトランザクション外で重複または保存障害へ変換する。 */
@Service
public class RegistrationSubmissionService {
    private final RegistrationService registration;
    private final UserRepository users;

    public RegistrationSubmissionService(RegistrationService registration, UserRepository users) {
        this.registration = registration;
        this.users = users;
    }

    @Transactional(propagation = Propagation.NEVER)
    public RegisterResponse register(RegisterRequest request) {
        try {
            return registration.register(request);
        } catch (RegistrationConflictException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException ex) {
            try {
                if (users.existsByUsername(request.username())) {
                    throw new RegistrationConflictException(Field.USERNAME);
                }
                if (users.existsByEmail(request.email())) {
                    throw new RegistrationConflictException(Field.EMAIL);
                }
            } catch (RegistrationConflictException conflict) {
                throw conflict;
            } catch (DataAccessException | TransactionException lookupFailure) {
                // 元の保存障害を公開用例外へ変換する。
            }
            throw new RegistrationOperationException(ex);
        }
    }
}
