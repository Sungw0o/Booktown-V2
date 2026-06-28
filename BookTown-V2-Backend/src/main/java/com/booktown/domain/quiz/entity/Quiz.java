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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "quiz",
        indexes = {
                @Index(name = "idx_quiz_user_id", columnList = "user_id"),
                @Index(name = "idx_quiz_book_id", columnList = "book_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "quiz_job_id")
    private Long quizJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuizDifficulty difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Quiz create(User user, Book book, Long quizJobId, QuizDifficulty difficulty, int questionCount) {
        Quiz quiz = new Quiz();
        quiz.user = user;
        quiz.book = book;
        quiz.quizJobId = quizJobId;
        quiz.difficulty = difficulty;
        quiz.questionCount = questionCount;
        return quiz;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
