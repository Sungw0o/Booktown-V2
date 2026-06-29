package com.booktown.domain.quiz.entity;

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
        name = "submission_answer",
        indexes = @Index(name = "idx_submission_answer_submission_id", columnList = "submission_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private QuizSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "selected_option_order", nullable = false)
    private int selectedOptionOrder;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static SubmissionAnswer create(QuizSubmission submission, Question question,
                                           int selectedOptionOrder, boolean correct) {
        SubmissionAnswer answer = new SubmissionAnswer();
        answer.submission = submission;
        answer.question = question;
        answer.selectedOptionOrder = selectedOptionOrder;
        answer.correct = correct;
        return answer;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
