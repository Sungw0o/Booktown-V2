package com.booktown.domain.illustration.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.entity.Scene;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.book.repository.SceneRepository;
import com.booktown.domain.illustration.document.IllustrationDocument;
import com.booktown.domain.illustration.dto.CreateIllustrationRequest;
import com.booktown.domain.illustration.dto.IllustrationJobResponse;
import com.booktown.domain.illustration.entity.IllustrationJob;
import com.booktown.domain.illustration.entity.IllustrationJobStatus;
import com.booktown.domain.illustration.entity.IllustrationStyle;
import com.booktown.domain.illustration.repository.IllustrationDocumentRepository;
import com.booktown.domain.illustration.repository.IllustrationJobRepository;
import com.booktown.domain.user.entity.User;
import com.booktown.domain.user.repository.UserRepository;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IllustrationServiceTest {

    private BookRepository bookRepository;
    private SceneRepository sceneRepository;
    private UserRepository userRepository;
    private IllustrationJobRepository illustrationJobRepository;
    private IllustrationDocumentRepository illustrationDocumentRepository;
    private IllustrationProcessor illustrationProcessor;
    private IllustrationService illustrationService;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        sceneRepository = mock(SceneRepository.class);
        userRepository = mock(UserRepository.class);
        illustrationJobRepository = mock(IllustrationJobRepository.class);
        illustrationDocumentRepository = mock(IllustrationDocumentRepository.class);
        illustrationProcessor = mock(IllustrationProcessor.class);
        illustrationService = new IllustrationService(bookRepository, sceneRepository, userRepository,
                illustrationJobRepository, illustrationDocumentRepository, illustrationProcessor);
    }

    @Test
    void getScenes_throws_when_book_not_found() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> illustrationService.getScenes(99L, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void createIllustration_throws_when_scene_not_found() {
        when(sceneRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> illustrationService.createIllustration(1L, 99L, new CreateIllustrationRequest("CLASSIC", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCENE_NOT_FOUND);
    }

    @Test
    void createIllustration_throws_when_invalid_style() {
        Book book = sampleBook();
        Scene scene = sampleScene(book);
        when(sceneRepository.findById(1L)).thenReturn(Optional.of(scene));
        when(illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> illustrationService.createIllustration(1L, 1L, new CreateIllustrationRequest("INVALID_STYLE", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void createIllustration_throws_when_duplicate_active_job() {
        Book book = sampleBook();
        Scene scene = sampleScene(book);
        when(sceneRepository.findById(1L)).thenReturn(Optional.of(scene));
        when(illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(eq(1L), eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> illustrationService.createIllustration(1L, 1L, new CreateIllustrationRequest("CLASSIC", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_ILLUSTRATION_JOB);
    }

    @Test
    void createIllustration_throws_when_rate_limit_exceeded() {
        Book book = sampleBook();
        Scene scene = sampleScene(book);
        when(sceneRepository.findById(1L)).thenReturn(Optional.of(scene));
        when(illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(illustrationJobRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(5);

        assertThatThrownBy(() -> illustrationService.createIllustration(1L, 1L, new CreateIllustrationRequest("CLASSIC", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ILLUSTRATION_RATE_LIMIT_EXCEEDED);
    }

    @Test
    void getIllustrationJob_throws_when_not_owner() {
        when(illustrationJobRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> illustrationService.getIllustrationJob(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ILLUSTRATION_JOB_NOT_FOUND);
    }

    @Test
    void getIllustrationJob_returns_response_for_owner() {
        Book book = sampleBook();
        Scene scene = sampleScene(book);
        User user = sampleUser();
        IllustrationJob job = IllustrationJob.create(user, scene, IllustrationStyle.CLASSIC, null);
        when(illustrationJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(job));

        IllustrationJobResponse response = illustrationService.getIllustrationJob(1L, 1L);

        assertThat(response.status()).isEqualTo(IllustrationJobStatus.QUEUED.name());
        assertThat(response.style()).isEqualTo("CLASSIC");
    }

    @Test
    void regenerateIllustration_throws_when_illustration_not_found() {
        when(illustrationDocumentRepository.findByIdAndUserId("docId", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> illustrationService.regenerateIllustration(1L, "docId"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ILLUSTRATION_NOT_FOUND);
    }

    @Test
    void regenerateIllustration_throws_when_duplicate_job() {
        IllustrationDocument doc = IllustrationDocument.create(1L, 1L, 10L, "CLASSIC", null, "https://img.test/1.png", false);
        when(illustrationDocumentRepository.findByIdAndUserId("docId", 1L)).thenReturn(Optional.of(doc));
        when(illustrationJobRepository.existsByUserIdAndSceneIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> illustrationService.regenerateIllustration(1L, "docId"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_ILLUSTRATION_JOB);
    }

    @Test
    void getIllustrations_throws_when_scene_not_found() {
        when(sceneRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> illustrationService.getIllustrations(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCENE_NOT_FOUND);
    }

    @Test
    void getIllustrations_returns_docs() {
        when(sceneRepository.existsById(1L)).thenReturn(true);
        IllustrationDocument doc = IllustrationDocument.create(1L, 1L, 10L, "CLASSIC", null, "https://img.test/1.png", false);
        when(illustrationDocumentRepository.findAllBySceneIdAndUserIdOrderByCreatedAtDesc(1L, 1L))
                .thenReturn(List.of(doc));

        var result = illustrationService.getIllustrations(1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).style()).isEqualTo("CLASSIC");
    }

    private Book sampleBook() {
        return Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
    }

    private Scene sampleScene(Book book) {
        return Scene.create(book, null, "첫 번째 장면", "비가 내리는 날...", 1);
    }

    private User sampleUser() {
        return User.local("test@test.com", "테스터", "hash");
    }
}
