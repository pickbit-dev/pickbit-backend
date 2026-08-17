package com.pickbit.library.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 읽을 수 없는 요청 본문이 500 이 아니라 400 으로 나가는지 검증합니다.
 *
 * <p>운영에서 CP949 로 깨진 한글 닉네임이 전송되자 500 이 나갔습니다.
 * 클라이언트 잘못인데 5xx 로 집계돼 에러 로그와 5xx 에러율 지표를 오염시켰습니다.
 */
class GlobalExceptionHandlerTest {

    private record Payload(String nickname) {
    }

    @RestController
    static class TestController {
        @PostMapping("/test")
        Payload echo(@RequestBody Payload payload) {
            return payload;
        }
    }

    /** GlobalExceptionHandler 는 추상이라 각 서비스처럼 구현체를 하나 둡니다. */
    @RestControllerAdvice
    static class TestExceptionHandler extends GlobalExceptionHandler {
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new TestExceptionHandler())
            .build();

    @Test
    @DisplayName("문법이 깨진 JSON 은 400 을 반환한다")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 UTF-8 바이트가 섞인 본문은 400 을 반환한다")
    void invalidUtf8ReturnsBadRequest() throws Exception {
        // 운영에서 실제로 터진 것과 같은 모양. 0xB7 은 UTF-8 시작 바이트가 될 수 없다.
        byte[] body = new byte[]{'{', '"', 'n', 'i', 'c', 'k', 'n', 'a', 'm', 'e', '"', ':', '"',
                (byte) 0xB7, '"', '}'};

        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("응답에 파서 내부 메시지가 새어 나가지 않는다")
    void responseDoesNotLeakInternals() throws Exception {
        MvcResult result = mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": "))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 고정 문구만 나가야 한다. e.getMessage() 를 그대로 실으면
        // "through reference chain: ...Payload[\"nickname\"]" 같은 내부 구조가 노출된다.
        assertThat(body).contains("요청 본문을 읽을 수 없습니다.");
        assertThat(body).doesNotContain("reference chain");
        assertThat(body).doesNotContain("Payload");
        assertThat(body).doesNotContain("com.pickbit");
    }

    @Test
    @DisplayName("정상 본문은 그대로 처리된다")
    void validJsonStillWorks() throws Exception {
        mockMvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"클로드\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }
}
