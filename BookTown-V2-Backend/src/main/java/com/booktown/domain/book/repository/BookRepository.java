package com.booktown.domain.book.repository;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findAllByGenre(Genre genre, Pageable pageable);

    @Query("SELECT b FROM Book b WHERE b.title LIKE %:q% OR b.author LIKE %:q%")
    Page<Book> searchByTitleOrAuthor(@Param("q") String q, Pageable pageable);
}
