package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.repository.SummaryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SummaryProcessor 의 DB 작업만 담당하는 트랜잭션 경계 전용 서비스.
 *
 * <p>왜 별도 빈으로 분리했는가
 * <ul>
 *   <li>기존 구조는 {@code @Async + @Transactional} 이 process() 메서드 <b>전체</b>를 감싸고 있어,
 *       내부의 OpenAI / Chroma 호출이 응답을 기다리는 동안 DB 커넥션을 계속 점유했다.</li>
 *   <li>같은 클래스 안에서 {@code @Transactional} 메서드를 나눠 호출하면
 *       Spring AOP 프록시를 거치지 않는 <b>self-invocation</b> 이 되어 트랜잭션이 적용되지 않는다.
 *       따라서 반드시 <b>다른 빈</b>으로 분리해 주입받아 호출해야 한다.</li>
 * </ul>
 *
 * <p>이 클래스의 모든 메서드는 외부 I/O 를 절대 수행하지 않는다.
 * 외부 호출이 필요한 로직은 {@link SummaryProcessor} 의 트랜잭션 밖 구간에 둔다.
 */
@Service
@RequiredArgsConstructor
public class SummaryJobTransactionService {

    private final SummaryJobRepository summaryJobRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;

    /**
     * 1단계 (짧은 트랜잭션): Job 을 PROCESSING 으로 바꾸고,
     * 트랜잭션 밖에서 외부 I/O 를 수행하는 데 필요한 데이터를 전부 <b>값</b>으로 추출해 반환한다.
     *
     * <p>엔티티가 아니라 {@link SummaryJobContext} record 를 반환하는 이유:
     * 트랜잭션과 영속성 컨텍스트가 닫힌 뒤 LAZY 프록시에 접근하면
     * LazyInitializationException 이 발생하기 때문이다.
     */
    @Transactional
    public SummaryJobContext startProcessing(Long jobId) {
        SummaryJob job = summaryJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("SummaryJob not found: " + jobId));
        job.markProcessing();

        Book book = job.getBook();
        List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(book.getId());

        return new SummaryJobContext(
                jobId,
                book.getId(),
                job.getUser().getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getDescription(),
                chapters.stream().map(ChapterContent::from).toList()
        );
    }

    /**
     * 2단계 (짧은 트랜잭션): AI 가 번역한 도서 메타데이터를 반영한다.
     *
     * <p>실패해도 요약 생성 자체는 계속되어야 하는 부가 작업이므로
     * {@link Propagation#REQUIRES_NEW} 로 분리해, 여기서 실패해도
     * 이후 markCompleted 트랜잭션에 영향을 주지 않게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBookMetadata(Long bookId, String title, String author, String description) {
        bookRepository.findById(bookId)
                .ifPresent(book -> book.updateCatalogMetadata(title, author, description));
    }

    /** 3단계 (짧은 트랜잭션): 결과 저장 후 COMPLETED 로 전이. */
    @Transactional
    public void markCompleted(Long jobId, String summaryDocumentId) {
        summaryJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("SummaryJob not found: " + jobId))
                .markCompleted(summaryDocumentId);
    }

    /**
     * 실패 처리 (짧은 트랜잭션).
     *
     * <p>{@link Propagation#REQUIRES_NEW} 를 쓰는 이유: 호출하는 쪽이 이미 롤백된
     * 트랜잭션 안에 있더라도 FAILED 상태 기록만큼은 반드시 커밋되어야 하기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage, boolean retryable) {
        summaryJobRepository.findById(jobId)
                .ifPresent(job -> job.markFailed(truncate(errorMessage), retryable));
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    /** 트랜잭션 밖으로 안전하게 전달하기 위한 불변 값 객체. */
    public record SummaryJobContext(
            Long jobId,
            Long bookId,
            Long userId,
            String title,
            String author,
            String description,
            List<ChapterContent> chapters
    ) {}
}
