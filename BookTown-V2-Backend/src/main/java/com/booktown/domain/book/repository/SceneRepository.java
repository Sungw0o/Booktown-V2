package com.booktown.domain.book.repository;

import com.booktown.domain.book.entity.Scene;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    Page<Scene> findAllByBookIdOrderBySceneOrderAsc(Long bookId, Pageable pageable);

    boolean existsByBookId(Long bookId);
}
