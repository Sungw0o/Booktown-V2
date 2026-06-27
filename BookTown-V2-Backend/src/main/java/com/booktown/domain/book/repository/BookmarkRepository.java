package com.booktown.domain.book.repository;

import com.booktown.domain.book.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    Optional<Bookmark> findByUserIdAndBookId(Long userId, Long bookId);

    @EntityGraph(attributePaths = {"book"})
    Page<Bookmark> findAllByUserId(Long userId, Pageable pageable);
}
