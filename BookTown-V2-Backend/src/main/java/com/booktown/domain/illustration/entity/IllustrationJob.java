package com.booktown.domain.illustration.entity;

import com.booktown.domain.book.entity.Scene;
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
        name = "illustration_job",
        indexes = {
                @Index(name = "idx_illustration_job_user_id", columnList = "user_id"),
                @Index(name = "idx_illustration_job_scene_id", columnList = "scene_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IllustrationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IllustrationStyle style;

    @Column(name = "prompt_hint", length = 500)
    private String promptHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IllustrationJobStatus status;

    @Column(name = "illustration_document_id")
    private String illustrationDocumentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean retryable = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static IllustrationJob create(User user, Scene scene, IllustrationStyle style, String promptHint) {
        IllustrationJob job = new IllustrationJob();
        job.user = user;
        job.scene = scene;
        job.style = style;
        job.promptHint = promptHint;
        job.status = IllustrationJobStatus.QUEUED;
        return job;
    }

    public void markProcessing() {
        this.status = IllustrationJobStatus.PROCESSING;
    }

    public void markCompleted(String illustrationDocumentId) {
        this.status = IllustrationJobStatus.COMPLETED;
        this.illustrationDocumentId = illustrationDocumentId;
    }

    public void markFailed(String errorMessage, boolean retryable) {
        this.status = IllustrationJobStatus.FAILED;
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
