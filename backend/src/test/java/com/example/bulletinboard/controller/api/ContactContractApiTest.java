package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.ContactCategory;
import com.example.bulletinboard.exception.ContactRateLimitException;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.ContactSubmissionService;
import com.example.bulletinboard.service.ContactRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** No.13の29ケース。実Security、保存・レート判定はモック。 */
@WebMvcTest(ContactApiController.class)
@Import(SecurityConfig.class)
class ContactContractApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ContactSubmissionService submission;
    @MockitoBean ContactRateLimitService limiter;
    @MockitoBean UserRepository users;

    private ObjectNode valid() {
        return mapper.valueToTree(Map.of("category", "OTHER", "email", "reply@example.com", "subject", "相談", "message", "本文"));
    }
    private org.springframework.test.web.servlet.ResultActions send(ObjectNode value) throws Exception {
        return mvc.perform(post("/api/contacts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(value)));
    }
    private ContactRequest captured() {
        var argument = ArgumentCaptor.forClass(ContactRequest.class);
        verify(submission).submitContact(argument.capture()); return argument.getValue();
    }

    @ParameterizedTest @EnumSource(ContactCategory.class)
    void allSixCategoriesWithoutName(ContactCategory category) throws Exception {
        var input=valid(); input.put("category", category.name());
        send(input).andExpect(status().isCreated()).andExpect(content().json("{\"message\":\"お問い合わせを受け付けました。\"}"));
        assertThat(captured().getCategory()).isEqualTo(category);
    }
    @ParameterizedTest @ValueSource(strings={"\"INVALID\"", "\"other\"", "\" OTHER \"", "0", "{}", "[]"})
    void invalidCategoryHasFieldError(String json) throws Exception {
        var input=valid(); input.set("category", mapper.readTree(json));
        send(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").isNotEmpty());
        verifyNoInteractions(submission);
    }
    @ParameterizedTest @ValueSource(strings={"", " \r\n\t", "\u3000\u00a0", "\ufeff\u2028\u2029"})
    void whitespaceOnlyMessageIsRejected(String value) throws Exception {
        var input=valid(); input.put("message", value);
        send(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").isNotEmpty());
        verifyNoInteractions(submission);
    }
    @ParameterizedTest @ValueSource(strings={"a b@example.com", "a\tb@example.com", "a\u00a0b@example.com", "a@exam\r\nple.com"})
    void emailInternalWhitespaceIsRejected(String value) throws Exception {
        var input=valid(); input.put("email", value);
        send(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        verifyNoInteractions(submission);
    }
    @ParameterizedTest @ValueSource(strings={"x\rBcc: attacker@example.com", "x\nBcc: attacker@example.com"})
    void subjectHeaderInjectionIsRejected(String value) throws Exception {
        var input=valid(); input.put("subject", value);
        send(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.subject").isNotEmpty());
        verifyNoInteractions(submission);
    }
    @ParameterizedTest @ValueSource(strings={"subject", "message"})
    void utf16BoundaryIsUsedWithoutTruncation(String field) throws Exception {
        int limit=field.equals("subject")?100:1000;
        var input=valid(); input.put(field, "😀".repeat(limit/2));
        send(input).andExpect(status().isCreated());
        var value=captured();
        assertThat(field.equals("subject")?value.getSubject():value.getMessage()).hasSize(limit);
        clearInvocations(submission);
        input.put(field, "😀".repeat(limit/2)+"a");
        send(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors."+field).isNotEmpty());
        verifyNoInteractions(submission);
    }
    @Test void normalizeBeforeLengthValidationAndPreserveMessage() throws Exception {
        String email="a".repeat(64)+"@"+"b".repeat(63)+"."+"c".repeat(63)+"."+"d".repeat(58)+".com";
        String whitespace=" \t\r\n\u3000\u00a0\ufeff";
        var input=valid(); input.put("email", whitespace+email+whitespace);
        input.put("subject", whitespace+"あ".repeat(100)+whitespace);
        input.put("message", " \r\n  文中\n\t末尾  \r\n");
        send(input).andExpect(status().isCreated());
        var value=captured(); assertThat(value.getEmail()).isEqualTo(email);
        assertThat(value.getSubject()).isEqualTo("あ".repeat(100));
        assertThat(value.getMessage()).isEqualTo(input.path("message").asText());
    }
    @Test void obsoleteNameIsNotCollected() throws Exception {
        var input=valid(); input.put("name", "この名前を保存しない");
        send(input).andExpect(status().isCreated());
        assertThat(mapper.valueToTree(captured()).has("name")).isFalse();
    }
    @Test void rateLimitHasFixed429AndSkipsSubmission() throws Exception {
        doThrow(new ContactRateLimitException()).when(limiter).check(anyString());
        send(valid()).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("短時間に多くのお問い合わせが送信されています。時間をおいてからお試しください。"))
                .andExpect(jsonPath("$.path").value("/api/contacts"));
        verifyNoInteractions(submission);
    }
    @Test void limiterFailureIsClosedAndDoesNotDiscloseDetails() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT secret FROM users test@example.com password=fake"))
                .when(limiter).check(anyString());
        var result=send(valid()).andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SELECT", "test@example.com", "password=fake");
        verifyNoInteractions(submission);
    }
    @Test void htmlIsKeptAsTextAndNotAddedToSuccessResponse() throws Exception {
        var input=valid(); input.put("message", "<script>alert('x')</script>\n普通の文章");
        var result=send(input).andExpect(status().isCreated()).andReturn();
        assertThat(captured().getMessage()).isEqualTo(input.path("message").asText());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("script", "reply@example.com");
    }
}
