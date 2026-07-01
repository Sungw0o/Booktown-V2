package com.booktown.domain.admin.service;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.RegisterBookRequest;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.domain.admin.entity.ContentJob;
import com.booktown.domain.admin.entity.ContentJobStatus;
import com.booktown.domain.admin.gutendex.GutendexBookDto;
import com.booktown.domain.admin.gutendex.GutendexBookSearchResponse;
import com.booktown.domain.admin.gutendex.GutendexClient;
import com.booktown.domain.admin.gutendex.GutendexMapper;
import com.booktown.domain.admin.gutendex.GutendexPageDto;
import com.booktown.domain.admin.repository.ContentJobRepository;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBookServiceTest {

    private BookRepository bookRepository;
    private ContentJobRepository contentJobRepository;
    private ContentProcessor contentProcessor;
    private GutendexClient gutendexClient;
    private GutendexMapper gutendexMapper;
    private AdminBookService adminBookService;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        contentJobRepository = mock(ContentJobRepository.class);
        contentProcessor = mock(ContentProcessor.class);
        gutendexClient = mock(GutendexClient.class);
        gutendexMapper = new GutendexMapper();
        adminBookService = new AdminBookService(bookRepository, contentJobRepository, contentProcessor, gutendexClient, gutendexMapper);
    }

    @Test
    void registerBook_saves_and_returns_response() {
        RegisterBookRequest request = new RegisterBookRequest("소나기", "황순원", "설명", null, "PROSE", "KOREA");
        Book book = Book.create("소나기", "황순원", "설명", null, Genre.PROSE, Country.KOREA);
        when(bookRepository.save(any(Book.class))).thenReturn(book);

        RegisterBookResponse response = adminBookService.registerBook(request);

        assertThat(response.title()).isEqualTo("소나기");
        assertThat(response.genre()).isEqualTo("PROSE");
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void registerBook_throws_when_genre_invalid() {
        RegisterBookRequest request = new RegisterBookRequest("소나기", "황순원", null, null, "INVALID", "KOREA");

        assertThatThrownBy(() -> adminBookService.registerBook(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void searchGutendexBooks_returns_mapped_results() {
        GutendexBookDto book = new GutendexBookDto(
                1342L,
                "Pride and Prejudice",
                List.of(new com.booktown.domain.admin.gutendex.GutendexAuthorDto("Austen, Jane", 1775, 1817)),
                List.of("A classic novel of manners."),
                List.of("Courtship -- Fiction", "England -- Fiction"),
                List.of("Best Books Ever Listings"),
                List.of("en"),
                false,
                Map.of(
                        "text/plain; charset=utf-8", "https://www.gutenberg.org/cache/epub/1342/pg1342.txt",
                        "image/jpeg", "https://www.gutenberg.org/cache/epub/1342/cover.jpg"
                ),
                1000
        );
        when(gutendexClient.search("pride", 1))
                .thenReturn(new GutendexPageDto(1, null, null, List.of(book)));

        GutendexBookSearchResponse response = adminBookService.searchGutendexBooks("pride", 1);

        assertThat(response.books()).hasSize(1);
        assertThat(response.books().get(0).gutenbergId()).isEqualTo(1342L);
        assertThat(response.books().get(0).textPlainUrl()).contains("pg1342.txt");
        assertThat(response.books().get(0).suggestedGenre()).isEqualTo("NOVEL");
    }

    @Test
    void uploadContent_throws_when_file_not_txt() {
        Book book = Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> adminBookService.uploadContent(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    void uploadContent_throws_when_file_too_large() {
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", largeContent);

        assertThatThrownBy(() -> adminBookService.uploadContent(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void uploadContent_throws_when_encoding_invalid() {
        byte[] invalidUtf8 = {(byte) 0xFF, (byte) 0xFE, 0x41};
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", invalidUtf8);

        assertThatThrownBy(() -> adminBookService.uploadContent(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_ENCODING_INVALID);
    }

    @Test
    void uploadContent_throws_when_book_not_found() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = validTxtFile();

        assertThatThrownBy(() -> adminBookService.uploadContent(99L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void uploadContent_throws_when_content_already_exists() {
        Book book = mock(Book.class);
        when(book.isHasContent()).thenReturn(true);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        MockMultipartFile file = validTxtFile();

        assertThatThrownBy(() -> adminBookService.uploadContent(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_CONTENT_ALREADY_EXISTS);
    }

    @Test
    void getContentJob_throws_when_not_found() {
        when(contentJobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminBookService.getContentJob(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTENT_JOB_NOT_FOUND);
    }

    @Test
    void getContentJob_returns_response() {
        Book book = Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
        ContentJob job = ContentJob.create(book);
        when(contentJobRepository.findById(1L)).thenReturn(Optional.of(job));

        ContentJobResponse response = adminBookService.getContentJob(1L);

        assertThat(response.status()).isEqualTo(ContentJobStatus.QUEUED.name());
        verify(contentJobRepository).findById(1L);
    }

    private MockMultipartFile validTxtFile() {
        byte[] content = "제1장 봄\n소년이 달려갔다.".getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile("file", "test.txt", "text/plain", content);
    }
}
