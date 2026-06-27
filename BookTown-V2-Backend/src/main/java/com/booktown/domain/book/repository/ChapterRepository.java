package com.booktown.domain.book.repository;

import com.booktown.domain.book.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findAllByBookIdOrderByChapterNumberAsc(Long bookId);
}
