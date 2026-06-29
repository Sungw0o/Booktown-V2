package com.booktown.domain.book.entity;

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
        name = "scene",
        indexes = @Index(name = "idx_scene_book_id", columnList = "book_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "scene_order", nullable = false)
    private int sceneOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Scene create(Book book, Chapter chapter, String title, String excerpt, int sceneOrder) {
        Scene scene = new Scene();
        scene.book = book;
        scene.chapter = chapter;
        scene.title = title;
        scene.excerpt = excerpt;
        scene.sceneOrder = sceneOrder;
        return scene;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
