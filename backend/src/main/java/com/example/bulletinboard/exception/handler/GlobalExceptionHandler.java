package com.example.bulletinboard.exception.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.exception.UserNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 【クラスの役割】
 * REST APIで発生した例外を共通で受け取り、
 * HTTP StatusとErrorResponseへ変換して
 * フロントエンドへJSON形式で返すクラスです。
 *
 * 各REST Controllerで個別にtry-catchを書くのではなく、
 * API全体の例外処理をこのクラスへ集約します。
 *
 * 【現在の対応内容】
 * - TopicNotFoundException
 *   → 404 Not Found
 *   → ErrorResponseを返却
 *
 * - IllegalArgumentException
 *   → 400 Bad Request
 *   → ErrorResponseを返却
 *
 * - UserNotFoundException
 *   → 500 Internal Server Error
 *   → ErrorResponseを返却
 *
 * 【今後の拡張予定】
 * - Validationエラー
 *   → 400 Bad Request
 * - 権限なし
 *   → 403 Forbidden
 * - 業務ルール上の編集不可
 *   → 409 Conflict
 *
 * ※ 旧Thymeleaf ControllerのFlashMessage処理とは分離し、
 *    REST API専用の例外処理として使用します。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Topicが存在しない、または論理削除済みの場合の
     * TopicNotFoundExceptionを処理します。
     *
     * @param ex      発生したTopicNotFoundException
     * @param request エラーが発生したHTTPリクエスト
     * @return 404 Not FoundとErrorResponse
     */
    @ExceptionHandler(TopicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTopicNotFound(
            TopicNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
      * 不正なリクエスト値などによって発生した
      * IllegalArgumentExceptionを処理します。
      *
      * 現在は主にTopic一覧検索で
      * keywordが空文字・空白だった場合に使用します。
      *
      * @param ex      発生したIllegalArgumentException
      * @param request エラーが発生したHTTPリクエスト
      * @return 400 Bad RequestとErrorResponse
      */
      @ExceptionHandler(IllegalArgumentException.class)
      public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request) {

           ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

         return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
      }

      /**
        * 認証情報に対応するUserを取得できなかった場合の
        * UserNotFoundExceptionを処理します。
        *
        * @param ex      発生したUserNotFoundException
        * @param request エラーが発生したHTTPリクエスト
        * @return 500 Internal Server ErrorとErrorResponse
        */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
        UserNotFoundException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
}
}