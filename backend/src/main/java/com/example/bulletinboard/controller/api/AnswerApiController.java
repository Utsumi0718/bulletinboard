package com.example.bulletinboard.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.answer.AnswerResponse;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * Answerに関するREST APIを提供するControllerです。
 *
 * 現在は、指定されたTopicに紐づくAnswer一覧取得を担当します。
 *
 * 業務ルールやデータ取得処理はService層へ委譲し、
 * ControllerではHTTPリクエストの受け取りと
 * Response DTOへの変換を行います。
 *
 * 【現在の対応API】
 * - GET /api/topics/{topicId}/answers
 *   → 指定TopicのAnswer一覧取得
 *   → 正常時 200 OK
 *   → Topic不存在時 404 Not Found
 *
 * 【今後追加予定】
 * - POST /api/topics/{topicId}/answers
 * - PUT /api/answers/{id}
 * - DELETE /api/answers/{id}
 */


@RestController
@RequestMapping("/api")
public class AnswerApiController {

  private final AnswerService answerService;
  private final TopicService topicService;


  public AnswerApiController(
              AnswerService answerService,
              TopicService topicService){
      this.answerService = answerService;
      this.topicService = topicService;
  }

  /**
 * 指定されたTopicに紐づくAnswer一覧を取得します。
 *
 * まずTopicServiceを使用して対象Topicが存在することを確認します。
 * Topicが存在しない、または論理削除済みの場合は、
 * TopicNotFoundExceptionが発生し、
 * GlobalExceptionHandlerによって404 Not Foundとして返されます。
 *
 * Topicが存在する場合は、
 * AnswerServiceから削除されていないAnswer一覧を取得し、
 * AnswerResponseへ変換して返します。
 *
 * Answerが0件の場合でもエラーにはせず、
 * 空のListを200 OKで返します。
 *
 * @param topicId Answer一覧を取得する対象TopicのID
 * @return AnswerResponseの一覧と200 OK
 */
@GetMapping("/topics/{topicId}/answers")
public ResponseEntity<List<AnswerResponse>> getAnswersByTopicId(
        @PathVariable Long topicId) {

    topicService.getById(topicId);

    List<AnswerResponse> responses =
            answerService.getAnswersByTopicId(topicId)
                    .stream()
                    .map(AnswerResponse::from)
                    .toList();

    return ResponseEntity.ok(responses);
}
}
