package com.booktown.global.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * perf 프로파일 전용 가짜 ChatClient. 커넥션 풀 경합 재현에는 "외부 호출이 오래 걸린다"는
 * 조건만 있으면 되고, 실제 OpenAI 호출은 응답 시간이 요동쳐 Before/After 비교를 무효화하므로
 * 지연 시간을 고정해 독립변수를 통제한다.
 */
@Profile("perf")
@Configuration
public class PerfFakeAiConfig {

    @Bean
    @Primary
    public ChatClient fakeChatClient(@Value("${booktown.perf.fake-ai-latency-ms:8000}") long latencyMs) {
        return ChatClient.create(new FixedLatencyFakeChatModel(latencyMs));
    }

    private static final class FixedLatencyFakeChatModel implements ChatModel {

        private final long latencyMs;

        private FixedLatencyFakeChatModel(long latencyMs) {
            this.latencyMs = latencyMs;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            sleep();
            String content = fakeContentFor(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }

        private void sleep() {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("perf fake AI call interrupted", e);
            }
        }

        private String fakeContentFor(String requestText) {
            if (requestText.contains("객관식 문제를 생성")) {
                return fakeQuizJson();
            }
            if (requestText.contains("TITLE: ...")) {
                return fakeMetadataTranslation();
            }
            return fakeSummaryText();
        }

        private String fakeQuizJson() {
            return """
                    [
                      {"question": "이 작품의 핵심 사건은 무엇인가?", "options": ["A", "B", "C", "D"], "answerIndex": 0, "explanation": "부하테스트용 고정 응답입니다."},
                      {"question": "주인공의 성격으로 가장 알맞은 것은?", "options": ["A", "B", "C", "D"], "answerIndex": 1, "explanation": "부하테스트용 고정 응답입니다."}
                    ]
                    """;
        }

        private String fakeMetadataTranslation() {
            return """
                    TITLE: 부하테스트용 고정 제목
                    AUTHOR: 부하테스트용 고정 저자
                    INTRO: 이 소개문은 트랜잭션 경계 부하테스트를 위해 고정된 응답입니다. 실제 번역 결과가 아닙니다.
                    """;
        }

        private String fakeSummaryText() {
            String paragraph = "이 문단은 트랜잭션 경계 부하테스트를 위해 고정된 길이의 한국어 요약 응답을 생성합니다. "
                    + "실제 OpenAI 호출 없이 커넥션 점유 시간만 재현하기 위한 더미 텍스트입니다. ";
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 800) {
                sb.append(paragraph);
            }
            return sb.toString();
        }
    }
}
