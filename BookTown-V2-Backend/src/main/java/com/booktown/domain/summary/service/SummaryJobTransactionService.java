package com.booktown.domain.summary.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Chapter;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.ChapterRepository;
import com.booktown.domain.summary.entity.SummaryJob;
import com.booktown.domain.summary.repository.SummaryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryJobTransactionService {

    private final SummaryJobRepository summaryJobRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;

    @Transactional
    public SummaryWork updateJobToProcessing(Long jobId) {
        SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        summaryJobRepository.save(job);

        Book book = job.getBook();
        List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(book.getId());
        return new SummaryWork(
                book.getId(),
                job.getUser().getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getDescription(),
                chapters
        );
    }

    @Transactional
    public void updateBookMetadata(Long bookId, String title, String author, String description) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        book.updateCatalogMetadata(title, author, description);
        bookRepository.save(book);
    }

    @Transactional
    public void updateJobToCompleted(Long jobId, String summaryDocumentId) {
        SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();
        job.markCompleted(summaryDocumentId);
        summaryJobRepository.save(job);
    }

    @Transactional
    public void updateJobToFailed(Long jobId, String errorMessage, boolean retryable) {
        SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();
        job.markFailed(errorMessage, retryable);
        summaryJobRepository.save(job);
    }

    public record SummaryWork(
            Long bookId,
            Long userId,
            String title,
            String author,
            String description,
            List<Chapter> chapters
    ) {
    }
}
