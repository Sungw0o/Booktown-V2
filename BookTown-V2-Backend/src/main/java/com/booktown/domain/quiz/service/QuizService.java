package com.booktown.domain.quiz.service;

import com.booktown.domain.book.entity.Book;
import com.booktown.domain.book.repository.BookRepository;
import com.booktown.domain.quiz.dto.AnswerResult;
import com.booktown.domain.quiz.dto.CreateQuizRequest;
import com.booktown.domain.quiz.dto.QuizDetailResponse;
import com.booktown.domain.quiz.dto.QuizHistoryItem;
import com.booktown.domain.quiz.dto.QuizJobResponse;
import com.booktown.domain.quiz.dto.QuizSubmissionResponse;
import com.booktown.domain.quiz.dto.QuestionResponse;
import com.booktown.domain.quiz.dto.SubmitQuizRequest;
import com.booktown.domain.quiz.entity.Question;
import com.booktown.domain.quiz.entity.QuestionOption;
import com.booktown.domain.quiz.entity.Quiz;
import com.booktown.domain.quiz.entity.QuizDifficulty;
import com.booktown.domain.quiz.entity.QuizJob;
import com.booktown.domain.quiz.entity.QuizSubmission;
import com.booktown.domain.quiz.entity.SubmissionAnswer;
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
import com.booktown.global.response.PageMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final QuizJobRepository quizJobRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final QuizProcessor quizProcessor;

    @Transactional
    public QuizJobResponse createQuiz(Long userId, Long bookId, CreateQuizRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));
        if (!book.isHasContent()) {
            throw new CustomException(ErrorCode.BOOK_CONTENT_NOT_READY);
        }

        QuizDifficulty difficulty = parseDifficulty(request.difficulty());
        validateQuestionCount(request.questionCount());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        QuizJob job = QuizJob.create(user, book, request.questionCount(), difficulty);
        quizJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                quizProcessor.process(job.getId());
            }
        });

        return QuizJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public QuizJobResponse getQuizJob(Long userId, Long jobId) {
        QuizJob job = quizJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_JOB_NOT_FOUND));
        return QuizJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getQuiz(Long userId, Long quizId) {
        Quiz quiz = quizRepository.findByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));

        List<Question> questions = questionRepository.findAllByQuizIdOrderByQuestionOrderAsc(quizId);
        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        List<QuestionOption> allOptions = questionOptionRepository.findAllByQuestionIdIn(questionIds);

        Map<Long, List<QuestionOption>> optionsByQuestion = allOptions.stream()
                .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        List<QuestionResponse> questionResponses = questions.stream()
                .map(q -> QuestionResponse.from(q, optionsByQuestion.getOrDefault(q.getId(), List.of())))
                .toList();

        return QuizDetailResponse.of(quiz, questionResponses);
    }

    @Transactional
    public QuizSubmissionResponse submitQuiz(Long userId, Long quizId, SubmitQuizRequest request) {
        Quiz quiz = quizRepository.findByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));

        if (quizSubmissionRepository.existsByQuizIdAndUserId(quizId, userId)) {
            throw new CustomException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        }

        List<Question> questions = questionRepository.findAllByQuizIdOrderByQuestionOrderAsc(quizId);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        Map<Long, Integer> answerMap = request.answers().stream()
                .collect(Collectors.toMap(SubmitQuizRequest.AnswerItem::questionId,
                        SubmitQuizRequest.AnswerItem::selectedOptionOrder));

        int correctCount = 0;
        List<AnswerResult> answerResults = new ArrayList<>();
        for (Question q : questions) {
            int selected = answerMap.getOrDefault(q.getId(), -1);
            boolean correct = selected == q.getCorrectOptionOrder();
            if (correct) correctCount++;
            answerResults.add(new AnswerResult(q.getId(), selected, q.getCorrectOptionOrder(), correct, q.getExplanation()));
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuizSubmission submission = QuizSubmission.create(quiz, user, correctCount, questions.size());
        quizSubmissionRepository.save(submission);

        List<SubmissionAnswer> submissionAnswers = new ArrayList<>();
        for (Question q : questions) {
            int selected = answerMap.getOrDefault(q.getId(), -1);
            submissionAnswers.add(SubmissionAnswer.create(submission, q, selected, selected == q.getCorrectOptionOrder()));
        }
        submissionAnswerRepository.saveAll(submissionAnswers);

        return QuizSubmissionResponse.of(submission, answerResults);
    }

    @Transactional(readOnly = true)
    public Page<QuizHistoryItem> getMyQuizHistory(Long userId, int page, int size) {
        return quizSubmissionRepository.findAllByUserIdOrderBySubmittedAtDesc(userId, PageRequest.of(page, size))
                .map(QuizHistoryItem::from);
    }

    private QuizDifficulty parseDifficulty(String difficulty) {
        try {
            return QuizDifficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateQuestionCount(int count) {
        if (count < 1 || count > 20) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
