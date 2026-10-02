package com.example.bulletinboard.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.example.bulletinboard.exception.WithdrawalException;

/** 退会トランザクションのDB障害を固定500応答用の例外へ変換する。 */
@Service
public class WithdrawalSubmissionService {
    private final WithdrawalService withdrawal;

    public WithdrawalSubmissionService(WithdrawalService withdrawal) {
        this.withdrawal = withdrawal;
    }

    public void withdraw(String email, String password) {
        try {
            withdrawal.withdraw(email, password);
        } catch (WithdrawalException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException ex) {
            throw new WithdrawalException(WithdrawalException.Reason.FAILED, ex);
        }
    }
}
