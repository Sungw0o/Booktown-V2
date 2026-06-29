package com.booktown.domain.quiz.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.entity.Country;
import com.booktown.domain.book.entity.Genre;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.quiz.dto.CreateQuizRequest;
import com.booktown.domain.quiz.dto.QuizJobResponse;
import com.booktown.domain.quiz.dto.QuizSubmissionResponse;
import com.booktown.domain.quiz.dto.SubmitQuizRequest;
import com.booktown.domain.quiz.entity.Question;
import com.booktown.domain.quiz.entity.Quiz;
import com.booktown.domain.quiz.entity.QuizDifficulty;
import com.booktown.domain.quiz.entity.QuizJob;
import com.booktown.domain.quiz.entity.QuizJobStatus;
import com.booktown.domain.quiz.repository.QuestionOptionRepository;
import com.booktown.domain.quiz.repository.QuestionRepository;
import com.booktown.domain.quiz.repository.QuizJobRepository;
import com.booktown.domain.quiz.repository.QuizRepository;
import com.booktown.domain.quiz.repository.QuizSubmissionRepository;
import com.booktown.domain.quiz.repository.SubmissionAnswerRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizServiceTest {

    private BookRepository bookRepository;
    private UserRepository userRepository;
    private QuizJobRepository quizJobRepository;
    private QuizRepository quizRepository;
    private QuestionRepository questionRepository;
    private QuestionOptionRepository questionOptionRepository;
    private QuizSubmissionRepository quizSubmissionRepository;
    private SubmissionAnswerRepository submissionAnswerRepository;
    private QuizProcessor quizProcessor;
    private QuizService quizService;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        quizJobRepository = mock(QuizJobRepository.class);
        quizRepository = mock(QuizRepository.class);
        questionRepository = mock(QuestionRepository.class);
        questionOptionRepository = mock(QuestionOptionRepository.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);
        submissionAnswerRepository = mock(SubmissionAnswerRepository.class);
        quizProcessor = mock(QuizProcessor.class);
        quizService = new QuizService(bookRepository, userRepository, quizJobRepository,
                quizRepository, questionRepository, questionOptionRepository,
                quizSubmissionRepository, submissionAnswerRepository, quizProcessor);
    }

    @Test
    void createQuiz_throws_when_book_not_found() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.createQuiz(1L, 99L, new CreateQuizRequest(null, 5, "NORMAL")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void createQuiz_throws_when_content_not_ready() {
        Book book = Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> quizService.createQuiz(1L, 1L, new CreateQuizRequest(null, 5, "NORMAL")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_CONTENT_NOT_READY);
    }

    @Test
    void createQuiz_throws_when_invalid_difficulty() {
        Book book = bookWithContent();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> quizService.createQuiz(1L, 1L, new CreateQuizRequest(null, 5, "ULTRA")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void createQuiz_throws_when_question_count_invalid() {
        Book book = bookWithContent();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> quizService.createQuiz(1L, 1L, new CreateQuizRequest(null, 25, "NORMAL")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void getQuizJob_throws_when_not_owner() {
        when(quizJobRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuizJob(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.QUIZ_JOB_NOT_FOUND);
    }

    @Test
    void getQuiz_throws_when_not_owner() {
        when(quizRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuiz(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    }

    @Test
    void submitQuiz_throws_when_already_submitted() {
        Book book = bookWithContent();
        User user = sampleUser();
        Quiz quiz = Quiz.create(user, book, 1L, QuizDifficulty.NORMAL, 5);
        when(quizRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(quiz));
        when(quizSubmissionRepository.existsByQuizIdAndUserId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> quizService.submitQuiz(1L, 1L,
                new SubmitQuizRequest(List.of(new SubmitQuizRequest.AnswerItem(1L, 2)))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.QUIZ_ALREADY_SUBMITTED);
    }

    @Test
    void submitQuiz_scores_correctly() {
        Book book = bookWithContent();
        User user = sampleUser();
        Quiz quiz = Quiz.create(user, book, 1L, QuizDifficulty.NORMAL, 2);
        when(quizRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(quiz));
        when(quizSubmissionRepository.existsByQuizIdAndUserId(any(), any())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Question q1 = mock(Question.class);
        when(q1.getId()).thenReturn(1L);
        when(q1.getCorrectOptionOrder()).thenReturn(2);
        when(q1.getExplanation()).thenReturn("해설1");

        Question q2 = mock(Question.class);
        when(q2.getId()).thenReturn(2L);
        when(q2.getCorrectOptionOrder()).thenReturn(3);
        when(q2.getExplanation()).thenReturn("해설2");

        when(questionRepository.findAllByQuizIdOrderByQuestionOrderAsc(any())).thenReturn(List.of(q1, q2));
        when(questionOptionRepository.findAllByQuestionIdIn(any())).thenReturn(List.of());
        when(quizSubmissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(submissionAnswerRepository.saveAll(any())).thenReturn(List.of());

        // q1 correct (selected 2 = correct), q2 wrong (selected 1 ≠ correct 3)
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                new SubmitQuizRequest.AnswerItem(1L, 2),
                new SubmitQuizRequest.AnswerItem(2L, 1)
        ));

        QuizSubmissionResponse response = quizService.submitQuiz(1L, 1L, request);

        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.answers().get(0).correct()).isTrue();
        assertThat(response.answers().get(1).correct()).isFalse();
    }

    private Book bookWithContent() {
        Book book = Book.create("소나기", "황순원", null, null, Genre.PROSE, Country.KOREA);
        book.markContentUploaded();
        return book;
    }

    private User sampleUser() {
        return User.local("test@test.com", "테스터", "hash");
    }
}
