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
        name = "question",
        indexes = @Index(name = "idx_question_quiz_id", columnList = "quiz_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "correct_option_order", nullable = false)
    private int correctOptionOrder;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Question create(Quiz quiz, String content, String explanation,
                                   int correctOptionOrder, int questionOrder) {
        Question q = new Question();
        q.quiz = quiz;
        q.content = content;
        q.explanation = explanation;
        q.correctOptionOrder = correctOptionOrder;
        q.questionOrder = questionOrder;
        return q;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
