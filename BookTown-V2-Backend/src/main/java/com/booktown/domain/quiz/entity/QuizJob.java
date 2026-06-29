package com.booktown.domain.quiz.entity;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "quiz_job",
        indexes = @Index(name = "idx_quiz_job_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuizDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizJobStatus status;

    @Column(name = "quiz_id")
    private Long quizId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static QuizJob create(User user, Book book, int questionCount, QuizDifficulty difficulty) {
        QuizJob job = new QuizJob();
        job.user = user;
        job.book = book;
        job.questionCount = questionCount;
        job.difficulty = difficulty;
        job.status = QuizJobStatus.QUEUED;
        return job;
    }

    public void markProcessing() {
        this.status = QuizJobStatus.PROCESSING;
    }

    public void markCompleted(Long quizId) {
        this.status = QuizJobStatus.COMPLETED;
        this.quizId = quizId;
    }

    public void markFailed(String errorMessage, boolean retryable) {
        this.status = QuizJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
