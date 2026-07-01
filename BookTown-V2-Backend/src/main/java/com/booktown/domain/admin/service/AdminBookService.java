package com.booktown.domain.admin.service;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.RegisterBookRequest;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.domain.admin.entity.ContentJob;
import com.booktown.domain.admin.gutendex.GutendexBookDto;
import com.booktown.domain.admin.gutendex.GutendexBookSearchResponse;
import com.booktown.domain.admin.gutendex.GutendexClient;
import com.booktown.domain.admin.gutendex.GutendexImportRequest;
import com.booktown.domain.admin.gutendex.GutendexImportResponse;
import com.booktown.domain.admin.gutendex.GutendexMapper;
import com.booktown.domain.admin.gutendex.GutendexPageDto;
import com.booktown.domain.admin.repository.ContentJobRepository;
import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminBookService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    private final BookRepository bookRepository;
    private final ContentJobRepository contentJobRepository;
    private final ContentProcessor contentProcessor;
    private final GutendexClient gutendexClient;
    private final GutendexMapper gutendexMapper;

    @Transactional
    public RegisterBookResponse registerBook(RegisterBookRequest request) {
        Genre genre = parseEnum(Genre.class, request.genre(), ErrorCode.INVALID_INPUT);
        Country country = parseEnum(Country.class, request.country(), ErrorCode.INVALID_INPUT);
        Book book = Book.create(request.title(), request.author(), request.description(),
                request.coverImageUrl(), genre, country);
        bookRepository.save(book);
        return new RegisterBookResponse(book.getId(), book.getTitle(), book.getAuthor(),
                book.getGenre().name(), book.getCountry().name());
    }

    @Transactional(readOnly = true)
    public GutendexBookSearchResponse searchGutendexBooks(String keyword, int page) {
        GutendexPageDto result = gutendexClient.search(keyword, page);
        return new GutendexBookSearchResponse(
                result.count(),
                gutendexClient.extractPage(result.next()).orElse(null),
                gutendexClient.extractPage(result.previous()).orElse(null),
                Optional.ofNullable(result.results()).orElse(java.util.List.of()).stream()
                        .map(gutendexMapper::toSearchItem)
                        .toList()
        );
    }

    @Transactional
    public GutendexImportResponse importGutendexBook(Long gutenbergId, GutendexImportRequest request) {
        GutendexBookDto gutendexBook = gutendexClient.getBook(gutenbergId);
        String textUrl = gutendexMapper.findTextPlainUrl(gutendexBook.formats())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
        Genre genre = resolveGenre(request, gutendexBook);
        Country country = resolveCountry(request);

        Book book = Book.create(
                gutendexBook.title(),
                gutendexMapper.firstAuthor(gutendexBook),
                gutendexMapper.firstSummary(gutendexBook),
                gutendexMapper.findCoverUrl(gutendexBook.formats()).orElse(null),
                genre,
                country
        );
        bookRepository.save(book);

        ContentJob job = ContentJob.create(book);
        contentJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                contentProcessor.processFromGutendex(job.getId(), textUrl);
            }
        });

        RegisterBookResponse bookResponse = new RegisterBookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getGenre().name(),
                book.getCountry().name()
        );
        return new GutendexImportResponse(bookResponse, ContentJobResponse.from(job));
    }

    @Transactional
    public ContentJobResponse uploadContent(Long bookId, MultipartFile file) {
        validateFile(file);
        byte[] bytes = readBytes(file);
        validateEncoding(bytes);

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        if (book.isHasContent()) {
            throw new CustomException(ErrorCode.BOOK_CONTENT_ALREADY_EXISTS);
        }

        ContentJob job = ContentJob.create(book);
        contentJobRepository.save(job);

        // 트랜잭션 커밋 후 비동기 처리 시작 — DB에 job이 없는 상태에서 async가 먼저 실행되는 race condition 방지
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                contentProcessor.process(job.getId(), bytes);
            }
        });

        return ContentJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public ContentJobResponse getContentJob(Long jobId) {
        ContentJob job = contentJobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_JOB_NOT_FOUND));
        return ContentJobResponse.from(job);
    }

    private void validateFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateEncoding(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            throw new CustomException(ErrorCode.FILE_ENCODING_INVALID);
        }
    }

    private Genre resolveGenre(GutendexImportRequest request, GutendexBookDto book) {
        if (request != null && request.genre() != null && !request.genre().isBlank()) {
            return parseEnum(Genre.class, request.genre(), ErrorCode.INVALID_INPUT);
        }
        return gutendexMapper.suggestGenre(book);
    }

    private Country resolveCountry(GutendexImportRequest request) {
        if (request != null && request.country() != null && !request.country().isBlank()) {
            return parseEnum(Country.class, request.country(), ErrorCode.INVALID_INPUT);
        }
        return Country.WESTERN;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, ErrorCode errorCode) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(errorCode);
        }
    }
}
