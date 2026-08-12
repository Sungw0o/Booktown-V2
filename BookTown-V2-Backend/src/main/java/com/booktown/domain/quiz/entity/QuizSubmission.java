package com.booktown.domain.quiz.entity;

import com.booktown.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "quiz_submission",
        indexes = {
                @Index(
                        name = "idx_quiz_submission_user_submitted_at",
                        columnList = "user_id, submitted_at DESC"
                ),
                @Index(name = "idx_quiz_submission_quiz_id", columnList = "quiz_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public static QuizSubmission create(Quiz quiz, User user, int correctCount, int totalCount) {
        QuizSubmission submission = new QuizSubmission();
        submission.quiz = quiz;
        submission.user = user;
        submission.correctCount = correctCount;
        submission.totalCount = totalCount;
        submission.score = totalCount == 0 ? 0 : (int) Math.round(100.0 * correctCount / totalCount);
        return submission;
    }

    @PrePersist
    void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }
}
