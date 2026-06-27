package com.booktown.domain.book.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "book",
        indexes = {
                @Index(name = "idx_book_genre", columnList = "genre"),
                @Index(name = "idx_book_bookmark_count", columnList = "bookmark_count")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Genre genre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Country country;

    @Column(name = "bookmark_count", nullable = false)
    private int bookmarkCount = 0;

    // content 업로드 완료 시 true — summary/quiz/illustration 기능 활성화 기준
    @Column(name = "has_content", nullable = false)
    private boolean hasContent = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Book create(String title, String author, String description,
                              String coverImageUrl, Genre genre, Country country) {
        Book book = new Book();
        book.title = title;
        book.author = author;
        book.description = description;
        book.coverImageUrl = coverImageUrl;
        book.genre = genre;
        book.country = country;
        return book;
    }

    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    public void decreaseBookmarkCount() {
        if (this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }

    public void markContentUploaded() {
        this.hasContent = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
