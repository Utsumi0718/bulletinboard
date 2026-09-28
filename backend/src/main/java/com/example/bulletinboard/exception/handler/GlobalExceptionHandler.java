package com.example.bulletinboard.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.dto.error.ValidationErrorResponse;
import com.example.bulletinboard.exception.AdminContactOperationException;
import com.example.bulletinboard.exception.AnswerEditConflictException;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ContactNotFoundException;
import com.example.bulletinboard.exception.ContactSaveException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.LikeConflictException;
import com.example.bulletinboard.exception.TopicEditConflictException;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.ReportOperationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.servlet.http.HttpServletRequest;
/**
 * 【クラスの役割】
 * REST APIで発生した例外を、HTTPステータスと
 * 共通エラーレスポンスDTOへ変換するクラスです。
 *
 * 各Controllerでの例外処理の重複を避け、
 * フロントエンドへ返すエラー形式を統一します。
 *
 * 【現在の対応内容】
 * - 400 Bad Request
 *   IllegalArgumentException
 *   HttpMessageNotReadableException
 *   MethodArgumentTypeMismatchException
 *   → ErrorResponseを返却
 *
 *   MethodArgumentNotValidException
 *   → フィールドごとのエラーを含むValidationErrorResponseを返却
 *
 * - 403 Forbidden
 *   ForbiddenOperationException
 *   → ErrorResponseを返却
 *
 * - 404 Not Found
 *   TopicNotFoundException
 *   AnswerNotFoundException
 *   ContactNotFoundException
 *   → ErrorResponseを返却
 *
 * - 409 Conflict
 *   TopicEditConflictException
 *   AnswerEditConflictException
 *   LikeConflictException
 *   → ErrorResponseを返却
 *
 * - 500 Internal Server Error
 *   UserNotFoundException
 *   ContactSaveException
 *   AdminContactOperationException
 *   → ErrorResponseを返却
 *
 * 【公開するメッセージ】
 * JSON読み取り失敗、パラメータの型変換失敗、
 * お問い合わせ受付の保存失敗、管理操作のDB・トランザクション障害には、
 * 公開用の固定メッセージを使用します。
 * これらの応答には、原因例外の詳細・SQL・入力本文を含めません。
 *
 * ページ番号・件数・IDなどを指定された型へ変換できない場合は、
 * MethodArgumentTypeMismatchExceptionを処理し、
 * 400 Bad RequestとErrorResponseを返します。
 * メッセージは「リクエストのパラメータ形式が正しくありません。」
 * に統一します。
 *
 * 入力検証エラーでは、共通メッセージと
 * フィールドごとの検証メッセージを返します。
 * 検証メッセージにも公開可能な文言を設定します。
 *
 * その他の対応例外では例外メッセージを返すため、
 * 例外を生成する側で公開可能な文言を設定します。
 *
 * 一覧・詳細取得・状態変更・削除APIからのAdminContactOperationExceptionには
 * 固定メッセージを返します。
 * 運営用ログには操作名・対象ID・例外の型のみを記録します。
 * 原因例外の詳細やスタックトレースは出力しません。
 * エラー時のDB最終状態やロールバック完了を断定しません。
 * この失敗ログはDBに保存する成功操作履歴とは別に出力します。
 *
 * 【適用範囲】
 * @RestControllerを付けたControllerのみを対象とします。
 * Thymeleafの画面を返す旧@Controllerには適用しません。
 * URLではなく、Controllerのアノテーションで対象を判定します。
 *
 * Spring Securityのフィルタで発生する認証・認可エラーは、
 * Security側で別途処理します。
 *
 * 【お問い合わせ関連の区別】
 * ContactNotFoundExceptionは対象不存在を404へ変換します。
 * ContactSaveExceptionはお問い合わせ受付の保存失敗専用です。
 * 管理機能の更新・削除失敗には流用しません。
 */

@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    /** 通報の公開応答・失敗ログに画像、文章、メール、SQL、Throwableを含めない。 */
    @ExceptionHandler(ReportOperationException.class)
    public ResponseEntity<ErrorResponse> handleReportOperation(ReportOperationException ex,
            HttpServletRequest request) {
        var reason = ex.getReason();
        if (reason.status.is5xxServerError()) {
            log.error("report_operation_error targetId={} errorType={}", ex.getTargetId(),
                    ex.getCause() == null ? "Unknown" : ex.getCause().getClass().getSimpleName());
        }
        return ResponseEntity.status(reason.status).header("Cache-Control", "no-store")
                .body(new ErrorResponse(reason.status.value(), reason.status.getReasonPhrase(),
                        reason.message, request.getRequestURI()));
    }

    /** 画像の例外詳細・ファイル名・メール・画像本体を公開応答やログへ含めない。 */
    @ExceptionHandler(TopicImageException.class)
    public ResponseEntity<ErrorResponse> handleTopicImage(TopicImageException ex, HttpServletRequest request) {
        var reason = ex.getReason();
        if (reason.status.is5xxServerError()) {
            log.error("topic_image_error errorType={}",
                    ex.getCause() == null ? "Unknown" : ex.getCause().getClass().getSimpleName());
        }
        return ResponseEntity.status(reason.status)
                .header("Cache-Control", "no-store")
                .body(new ErrorResponse(reason.status.value(), reason.status.getReasonPhrase(),
                        reason.message, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageTooLarge(MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return handleTopicImage(new TopicImageException(TopicImageException.Reason.TOO_LARGE), request);
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleInvalidMultipart(Exception ex, HttpServletRequest request) {
        return handleTopicImage(new TopicImageException(TopicImageException.Reason.INVALID), request);
    }

     private static final Logger log =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
      * Topic一覧検索の空のkeywordや、管理者向けお問い合わせ一覧の
      * ページ番号・件数の範囲外、ステータス不正などに使用します。
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

/**
 * @ValidによるリクエストボディのValidationに失敗した場合の
 * MethodArgumentNotValidExceptionを処理します。
 *
 * フィールドごとのValidationエラーをMapへ変換し、
 * 400 Bad RequestとValidationErrorResponseを返します。
 *
 * @param ex      発生したMethodArgumentNotValidException
 * @param request エラーが発生したHTTPリクエスト
 * @return 400 Bad RequestとValidationErrorResponse
 */
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

      Map<String, String> fieldErrors = new LinkedHashMap<>();

    ex.getBindingResult()
            .getFieldErrors()
            .forEach(fieldError ->
                    fieldErrors.putIfAbsent(
                            fieldError.getField(),
                            fieldError.getDefaultMessage()
                    )
            );

    ValidationErrorResponse response =
            new ValidationErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "入力内容に誤りがあります。",
                    request.getRequestURI(),
                    fieldErrors
            );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
}

/**
 * 認証済みユーザーが権限を持たない操作を行おうとした場合の
 * ForbiddenOperationExceptionを処理します。
 *
 * @param ex      発生したForbiddenOperationException
 * @param request エラーが発生したHTTPリクエスト
 * @return 403 ForbiddenとErrorResponse
 */
@ExceptionHandler(ForbiddenOperationException.class)
public ResponseEntity<ErrorResponse> handleForbiddenOperation(
        ForbiddenOperationException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
}

/**
 * Topic編集時に業務ルール上の競合が発生した場合の
 * TopicEditConflictExceptionを処理します。
 *
 * @param ex      発生したTopicEditConflictException
 * @param request エラーが発生したHTTPリクエスト
 * @return 409 ConflictとErrorResponse
 */
@ExceptionHandler(TopicEditConflictException.class)
public ResponseEntity<ErrorResponse> handleTopicEditConflict(
        TopicEditConflictException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
}

/**
 * Answerが存在しない、または論理削除済みの場合の
 * AnswerNotFoundExceptionを処理します。
 *
 * @param ex      発生したAnswerNotFoundException
 * @param request エラーが発生したHTTPリクエスト
 * @return 404 Not FoundとErrorResponse
 */
@ExceptionHandler(AnswerNotFoundException.class)
public ResponseEntity<ErrorResponse> handleAnswerNotFound(
        AnswerNotFoundException ex,
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
 * Answer編集時に業務ルール上の競合が発生した場合の
 * AnswerEditConflictExceptionを処理します。
 *
 * 現在は、Likeが1件以上付いているAnswerを
 * 編集しようとした場合に使用します。
 *
 * @param ex      発生したAnswerEditConflictException
 * @param request エラーが発生したHTTPリクエスト
 * @return 409 ConflictとErrorResponse
 */
@ExceptionHandler(AnswerEditConflictException.class)
public ResponseEntity<ErrorResponse> handleAnswerEditConflict(
        AnswerEditConflictException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
}



/**
 * Like登録時に競合が発生した場合の
 * LikeConflictExceptionを処理します。
 *
 * 主に同一User + AnswerへのLike登録が
 * UNIQUE制約と競合した場合に使用します。
 *
 * @param ex      発生したLikeConflictException
 * @param request エラーが発生したHTTPリクエスト
 * @return 409 ConflictとErrorResponse
 */
@ExceptionHandler(LikeConflictException.class)
public ResponseEntity<ErrorResponse> handleLikeConflictException(
        LikeConflictException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
}

/**
 * JSONの構文不正など、リクエスト本文を読み取れない場合に
 * 400 Bad Requestと共通エラーレスポンスを返します。
 *
 * 解析例外の詳細や入力本文は返さず、
 * 公開用の固定メッセージを使用します。
 */
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "リクエストの形式が正しくありません。",
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
}

/**
 * お問い合わせの保存失敗を500の共通エラーレスポンスへ変換します。
 *
 * 原因例外のメッセージやSQLなどの内部情報は返しません。
 * 保存失敗のログはContactSubmissionService側で記録します。
 */
@ExceptionHandler(ContactSaveException.class)
public ResponseEntity<ErrorResponse> handleContactSaveException(
        ContactSaveException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "お問い合わせを受け付けられませんでした。"
                    + "時間をおいて再度お試しください。",
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
}

/**
 * 指定されたお問い合わせが存在しない場合の例外を処理します。
 *
 * @param ex      発生したContactNotFoundException
 * @param request エラーが発生したHTTPリクエスト
 * @return 404 Not Foundと共通エラーレスポンス
 */
@ExceptionHandler(ContactNotFoundException.class)
public ResponseEntity<ErrorResponse> handleContactNotFound(
        ContactNotFoundException ex,
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
 * リクエストパラメータやパス変数を、
 * 指定された型へ変換できない場合に400を返します。
 *
 * 入力値や内部の例外詳細をメッセージへ含めず、
 * 公開用の固定メッセージを使用します。
 * pathにはリクエストURIを設定します。
 *
 * @param ex      型変換に失敗した例外
 * @param request エラーが発生したHTTPリクエスト
 * @return 400 Bad Requestと共通エラーレスポンス
 */
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "リクエストのパラメータ形式が正しくありません。",
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
}

/**
 * お問い合わせの一覧・詳細取得・状態変更・削除に関するDB・トランザクション障害を
 * 500と固定メッセージのErrorResponseへ変換します。
 *
 * ログには操作名・対象ID・例外の型だけを記録します。
 * 一覧取得（LIST）では対象IDはnullです。
 * 原因例外のメッセージ・スタックトレース・認証メール・本文は出しません。
 *
 * コミット時の通信障害などではDBの最終状態を断定できないため、
 * ロールバック済みとは記録せず、操作エラーとして扱います。
 */
@ExceptionHandler(AdminContactOperationException.class)
public ResponseEntity<ErrorResponse> handleAdminContactOperation(
        AdminContactOperationException ex,
        HttpServletRequest request) {

    String errorType = ex.getCause() == null
            ? "Unknown"
            : ex.getCause().getClass().getSimpleName();

    // 最後の引数にもThrowableを渡さない。
    log.error(
            "admin_contact_operation_error operation={} contactId={} errorType={}",
            ex.getOperation(),
            ex.getContactId(),
            errorType
    );

    ErrorResponse response = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "お問い合わせの管理処理でエラーが発生しました。"
                    + "画面を再読み込みして状態を確認してください。",
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
}
}
