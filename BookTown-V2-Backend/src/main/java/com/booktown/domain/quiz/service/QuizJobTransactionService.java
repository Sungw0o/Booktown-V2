package com.booktown.domain.quiz.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.quiz.entity.Question;
import com.booktown.domain.quiz.entity.QuestionOption;
import com.booktown.domain.quiz.entity.Quiz;
import com.booktown.domain.quiz.entity.QuizDifficulty;
import com.booktown.domain.quiz.entity.QuizJob;
import com.booktown.domain.quiz.repository.QuestionOptionRepository;
import com.booktown.domain.quiz.repository.QuestionRepository;
import com.booktown.domain.quiz.repository.QuizJobRepository;
import com.booktown.domain.quiz.repository.QuizRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * QuizProcessor 의 DB 작업만 담당하는 트랜잭션 경계 전용 서비스.
 *
 * <p>{@link QuizProcessor} 는 AI 호출 구간을 트랜잭션 밖에 두기 위해
 * {@code @Transactional} 을 갖지 않으며, DB 작업은 전부 이 빈에 위임한다.
 * 같은 클래스 내부 호출(self-invocation)은 프록시를 거치지 않아
 * 트랜잭션이 걸리지 않으므로 반드시 별도 빈이어야 한다.
 *
 * <p>이 클래스의 메서드는 외부 I/O 를 수행하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class QuizJobTransactionService {

    private final QuizJobRepository quizJobRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;

    /** 1단계 (짧은 TX): PROCESSING 전이 + AI 호출에 필요한 데이터를 값으로 추출. */
    @Transactional
    public QuizJobContext startProcessing(Long jobId) {
        QuizJob job = quizJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("QuizJob not found: " + jobId));
        job.markProcessing();

        Long bookId = job.getBook().getId();
        List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(bookId);

        return new QuizJobContext(
                jobId,
                bookId,
                job.getUser().getId(),
                job.getQuestionCount(),
                job.getDifficulty(),
                chapters.stream()
                        .map(c -> new ChapterExcerpt(c.getTitle(), c.getContent()))
                        .toList()
        );
    }

    /**
     * 2단계 (짧은 TX): AI 가 생성한 문항을 저장하고 Job 을 COMPLETED 로 전이한다.
     *
     * <p>퀴즈·문항·선택지 저장과 Job 상태 전이를 <b>하나의 트랜잭션</b>으로 묶는다.
     * 문항 일부만 저장된 채 COMPLETED 가 되는 부분 저장을 막기 위함이다.
     *
     * @return 생성된 quizId
     */
    @Transactional
    public Long saveQuizAndComplete(QuizJobContext ctx, List<GeneratedQuestion> generatedQuestions) {
        QuizJob job = quizJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new IllegalStateException("QuizJob not found: " + ctx.jobId()));
        User user = userRepository.findById(ctx.userId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + ctx.userId()));
        Book book = bookRepository.findById(ctx.bookId())
                .orElseThrow(() -> new IllegalStateException("Book not found: " + ctx.bookId()));

        Quiz quiz = Quiz.create(user, book, ctx.jobId(), ctx.difficulty(), ctx.questionCount());
        quizRepository.save(quiz);

        int limit = Math.min(generatedQuestions.size(), ctx.questionCount());
        for (int i = 0; i < limit; i++) {
            GeneratedQuestion gq = generatedQuestions.get(i);
            Question question = Question.create(quiz, gq.content(), gq.explanation(), gq.correctOption(), i + 1);
            questionRepository.save(question);

            List<QuestionOption> options = new ArrayList<>();
            for (int j = 0; j < gq.options().size(); j++) {
                options.add(QuestionOption.create(question, gq.options().get(j), j + 1));
            }
            questionOptionRepository.saveAll(options);
        }

        job.markCompleted(quiz.getId());
        return quiz.getId();
    }

    /** 실패 처리 (짧은 TX). 호출 측 트랜잭션 상태와 무관하게 반드시 커밋되도록 REQUIRES_NEW. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage, boolean retryable) {
        quizJobRepository.findById(jobId)
                .ifPresent(job -> job.markFailed(truncate(errorMessage), retryable));
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    /** 트랜잭션 밖으로 전달하기 위한 불변 값 객체. */
    public record QuizJobContext(
            Long jobId,
            Long bookId,
            Long userId,
            int questionCount,
            QuizDifficulty difficulty,
            List<ChapterExcerpt> chapters
    ) {}

    public record ChapterExcerpt(String title, String content) {
        public boolean hasContent() {
            return content != null && !content.isBlank();
        }
    }

    /** AI 응답을 파싱한 결과. 저장 계층이 AI 응답 포맷에 직접 의존하지 않도록 분리. */
    public record GeneratedQuestion(String content, String explanation, int correctOption, List<String> options) {}
}
