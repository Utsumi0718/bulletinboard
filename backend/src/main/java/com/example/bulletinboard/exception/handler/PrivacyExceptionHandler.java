package com.example.bulletinboard.exception.handler;

import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import com.example.bulletinboard.controller.api.PrivacyAdminApiController;
import com.example.bulletinboard.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

/** 保持内容、暗号鍵、SQLや原因例外を応答・ログへ出さない。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PrivacyAdminApiController.class)
public class PrivacyExceptionHandler {
    @ExceptionHandler({IllegalStateException.class,DataAccessException.class,TransactionException.class})
    public ResponseEntity<ErrorResponse> failure(RuntimeException ex,HttpServletRequest request) {
        return ResponseEntity.internalServerError().header("Cache-Control","no-store")
                .body(new ErrorResponse(500,"Internal Server Error","個人情報の処理に失敗しました。",request.getRequestURI()));
    }
}
