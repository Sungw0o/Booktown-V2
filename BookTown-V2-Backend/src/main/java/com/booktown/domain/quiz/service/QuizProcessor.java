package com.booktown.domain.quiz.service;

import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.quiz.entity.Question;
import com.booktown.domain.quiz.entity.QuestionOption;
import com.booktown.domain.quiz.entity.Quiz;
import com.booktown.domain.quiz.entity.QuizDifficulty;
import com.booktown.domain.quiz.entity.QuizJob;
import com.booktown.domain.quiz.entity.QuizJobStatus;
import com.booktown.domain.quiz.repository.QuestionOptionRepository;
import com.booktown.domain.quiz.repository.QuestionRepository;
import com.booktown.domain.quiz.repository.QuizJobRepository;
import com.booktown.domain.quiz.repository.QuizRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuizProcessor {

    private static final String QUIZ_PROMPT = """
            당신은 한국 문학 교육 전문가입니다. 아래 도서 내용을 바탕으로 객관식 문제를 생성해주세요.

            도서 내용:
            %s

            요구사항:
            - 문항 수: %d개
            - 난이도: %s
            - 각 문제는 4지 선다형
            - 반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트 없이 JSON 배열만 출력하세요.

            JSON 형식:
            [
              {
                "content": "문제 내용",
                "explanation": "해설 (정답 이유 설명)",
                "correctOption": 1,
                "options": ["선택지1", "선택지2", "선택지3", "선택지4"]
              }
            ]
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final QuizJobRepository quizJobRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final ChatClient chatClient;

    @Async("quizProcessingExecutor")
    @Transactional
    public void process(Long jobId) {
        QuizJob job = quizJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        quizJobRepository.save(job);

        try {
            List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(job.getBook().getId());
            String context = buildContext(chapters, job.getQuestionCount());
            String prompt = String.format(QUIZ_PROMPT, context, job.getQuestionCount(), job.getDifficulty().name());

            String aiResponse = chatClient.prompt().user(prompt).call().content();
            String json = extractJson(aiResponse);
            List<AiQuestion> aiQuestions = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

            User user = userRepository.findById(job.getUser().getId()).orElseThrow();
            Quiz quiz = Quiz.create(user, job.getBook(), jobId, job.getDifficulty(), job.getQuestionCount());
            quizRepository.save(quiz);

            for (int i = 0; i < Math.min(aiQuestions.size(), job.getQuestionCount()); i++) {
                AiQuestion aq = aiQuestions.get(i);
                Question question = Question.create(quiz, aq.content(), aq.explanation(), aq.correctOption(), i + 1);
                questionRepository.save(question);

                List<QuestionOption> opts = new ArrayList<>();
                for (int j = 0; j < aq.options().size(); j++) {
                    opts.add(QuestionOption.create(question, aq.options().get(j), j + 1));
                }
                questionOptionRepository.saveAll(opts);
            }

            job.markCompleted(quiz.getId());
            quizJobRepository.save(job);
            log.info("QuizJob {} completed: quizId={}", jobId, quiz.getId());
        } catch (Exception e) {
            log.error("QuizJob {} failed: {}", jobId, e.getMessage(), e);
            boolean retryable = !(e instanceof IllegalArgumentException);
            job.markFailed(e.getMessage(), retryable);
            quizJobRepository.save(job);
        }
    }

    private String buildContext(List<Chapter> chapters, int questionCount) {
        int maxCharsPerChapter = Math.max(500, 3000 / Math.max(chapters.size(), 1));
        StringBuilder sb = new StringBuilder();
        for (Chapter ch : chapters) {
            if (ch.getContent() != null && !ch.getContent().isBlank()) {
                String excerpt = ch.getContent().length() > maxCharsPerChapter
                        ? ch.getContent().substring(0, maxCharsPerChapter)
                        : ch.getContent();
                sb.append("[").append(ch.getTitle()).append("]\n").append(excerpt).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start == -1 || end == -1 || start > end) {
            throw new IllegalArgumentException("AI 응답에서 JSON 배열을 찾을 수 없습니다.");
        }
        return response.substring(start, end + 1);
    }

    private record AiQuestion(String content, String explanation, int correctOption, List<String> options) {}
}
