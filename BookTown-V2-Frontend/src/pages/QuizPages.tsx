import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, History, Loader2, Play, RotateCcw } from 'lucide-react';
import { getBookById } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import {
  createQuiz,
  getMyQuizHistory,
  getQuiz,
  getQuizJob,
  submitQuiz,
} from '../api/quizApi';
import type {
  CreateQuizRequest,
  Quiz,
  QuizDifficulty,
  QuizHistoryItem,
  QuizJob,
  QuizSubmissionResult,
} from '../api/quizApi';
import { DArrow, DBack, DCheck, DkTabs, DkTopNav, DX as DXIcon, GlassBtn } from '../components/Primitives';
import { useAuth } from '../hooks/useAuth';

type QuizPhase = 'setup' | 'job' | 'solving';

interface ScoreRingProps {
  score: number;
  total: number;
  size?: number;
}

interface ResultState {
  book: Book;
  quiz: Quiz;
  result: QuizSubmissionResult;
}

const DIFFICULTY_LABELS: Record<QuizDifficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
};

const JOB_STEPS = ['QUEUED', 'PROCESSING', 'COMPLETED'] as const;

const ScoreRing: React.FC<ScoreRingProps> = ({ score, total, size = 96 }) => {
  const pct = total > 0 ? score / total : 0;
  const r = size / 2 - 8;
  const circ = 2 * Math.PI * r;
  const strokeColor = pct >= 0.8 ? '#5FC9A0' : pct >= 0.6 ? '#E8B86F' : '#D46A6A';

  return (
    <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="6" />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={strokeColor}
        strokeWidth="6"
        strokeLinecap="round"
        strokeDasharray={`${circ * pct} ${circ}`}
        style={{ transition: 'stroke-dasharray 1s ease' }}
      />
    </svg>
  );
};

interface QuizLayoutProps {
  book: Book;
  subtitle: string;
  activeTab: string;
  mobileContent: React.ReactNode;
  desktopContent: React.ReactNode;
}

const QuizLayout: React.FC<QuizLayoutProps> = ({ book, subtitle, activeTab, mobileContent, desktopContent }) => {
  const navigate = useNavigate();
  const { user, logout, isAuthenticated } = useAuth();

  const handleGo = (tab: string) => {
    if (tab === 'home' || tab === 'search' || tab === 'history') {
      navigate('/');
    } else if (tab === 'me') {
      navigate('/me');
    } else if (tab === 'admin') {
      navigate('/admin');
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen dk-surface flex flex-col relative overflow-hidden text-white selection:bg-purple-500 selection:text-white">
      <div className="dk-grain absolute inset-0 opacity-40 pointer-events-none" />

      <div className="md:hidden flex-1 flex flex-col">
        <div className="h-[52px] px-4 flex items-center justify-between border-b border-white/5 relative z-10 shrink-0 bg-[#0F0E13]/80 backdrop-blur-md">
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => navigate(`/books/${book.id}`)}
              className="text-white/45 active:text-white transition shrink-0"
              aria-label="상세 페이지로 이동"
            >
              <DBack className="w-5 h-5" />
            </button>
            <div className="min-w-0">
              <div className="text-[14px] leading-tight font-bold truncate">{book.title}</div>
              <div className="text-[9px] text-white/35 font-mono uppercase tracking-wider">{subtitle}</div>
            </div>
          </div>
          <button onClick={handleLogout} className="text-[10px] font-mono uppercase tracking-[0.14em] text-white/45 hover:text-white transition">
            LOGOUT
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 pb-[96px] pt-4">{mobileContent}</div>
        <DkTabs active={activeTab} go={handleGo} />
      </div>

      <div className="hidden md:flex flex-col flex-1 relative">
        <DkTopNav
          active="home"
          go={handleGo}
          onLogout={isAuthenticated ? handleLogout : undefined}
          nickname={user?.nickname || '민'}
        />
        <div className="flex-1 overflow-y-auto pt-24 px-10 py-8 max-w-6xl mx-auto w-full">{desktopContent}</div>
      </div>
    </div>
  );
};

const LoadingScreen: React.FC = () => (
  <div className="min-h-screen dk-surface flex items-center justify-center">
    <Loader2 className="w-10 h-10 text-purple-500 animate-spin" />
  </div>
);

const ErrorScreen: React.FC<{ message: string; onBack?: () => void }> = ({ message, onBack }) => (
  <div className="min-h-screen dk-surface flex items-center justify-center p-6">
    <div className="glass p-8 rounded-2xl text-center max-w-md">
      <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-4" />
      <p className="text-white/70 text-sm leading-relaxed">{message}</p>
      {onBack && (
        <button onClick={onBack} className="mt-6 px-5 py-2 rounded-full bg-white text-[#0B0E14] text-sm font-medium">
          돌아가기
        </button>
      )}
    </div>
  </div>
);

const makeInitialOptions = (book: Book | null): CreateQuizRequest => ({
  chapterFrom: 1,
  chapterTo: Math.min(3, Math.max(1, book?.chapters.length ?? 3)),
  questionCount: 5,
  difficulty: 'NORMAL',
});

export const QuizPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { isMockMode } = useAuth();
  const navigate = useNavigate();

  const [book, setBook] = useState<Book | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<QuizPhase>('setup');
  const [options, setOptions] = useState<CreateQuizRequest>(() => makeInitialOptions(null));
  const [job, setJob] = useState<QuizJob | null>(null);
  const [quiz, setQuiz] = useState<Quiz | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [currentIndex, setCurrentIndex] = useState(0);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!bookId) return;
    const fetchBook = async () => {
      setLoading(true);
      try {
        const data = await getBookById(isMockMode, bookId);
        setBook(data);
        setOptions(makeInitialOptions(data));
      } catch (err) {
        setError(err instanceof Error ? err.message : '도서 정보를 가져오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };
    fetchBook();
  }, [bookId, isMockMode]);

  useEffect(() => {
    if (!job || !bookId || phase !== 'job') return;
    if (job.status === 'FAILED') {
      return;
    }
    if (job.status === 'COMPLETED' && job.quizId) {
      getQuiz(isMockMode, bookId, job.quizId)
        .then((data) => {
          setQuiz(data);
          setAnswers({});
          setCurrentIndex(0);
          setPhase('solving');
        })
        .catch((err) => setError(err instanceof Error ? err.message : '생성된 퀴즈를 불러오지 못했습니다.'));
      return;
    }

    const timer = window.setTimeout(async () => {
      try {
        setJob(await getQuizJob(isMockMode, job.jobId));
      } catch (err) {
        setError(err instanceof Error ? err.message : '퀴즈 생성 상태를 확인하지 못했습니다.');
      }
    }, 1200);

    return () => window.clearTimeout(timer);
  }, [bookId, isMockMode, job, phase]);

  const selectedCount = useMemo(() => Object.keys(answers).length, [answers]);

  if (loading) return <LoadingScreen />;
  if (error || !book || !bookId) {
    return <ErrorScreen message={error || '도서를 찾을 수 없습니다.'} onBack={() => navigate('/')} />;
  }

  const maxChapter = Math.max(1, book.chapters.length || 1);
  const currentQuestion = quiz?.questions[currentIndex];
  const progress = quiz ? Math.round((selectedCount / quiz.questions.length) * 100) : 0;

  const updateOptions = (next: Partial<CreateQuizRequest>) => {
    setOptions((prev) => {
      const merged = { ...prev, ...next };
      if (merged.chapterFrom > merged.chapterTo) {
        merged.chapterTo = merged.chapterFrom;
      }
      return merged;
    });
  };

  const startQuiz = async () => {
    setError(null);
    setValidationError(null);
    setPhase('job');
    try {
      const chapterIds = book.chapters
        .filter((chapter) => chapter.chapterNumber >= options.chapterFrom && chapter.chapterNumber <= options.chapterTo)
        .map((chapter) => Number(chapter.id))
        .filter(Number.isFinite);
      const created = await createQuiz(isMockMode, book.id, { ...options, chapterIds });
      setJob(created.status === 'COMPLETED' ? created : { ...created, status: created.status ?? 'QUEUED' });
    } catch (err) {
      setPhase('setup');
      setError(err instanceof Error ? err.message : '퀴즈 생성을 요청하지 못했습니다.');
    }
  };

  const pickAnswer = (questionId: string, optionId: string) => {
    setValidationError(null);
    setAnswers((prev) => ({ ...prev, [questionId]: optionId }));
  };

  const goNext = () => {
    if (!currentQuestion || !answers[currentQuestion.id]) {
      setValidationError('다음으로 넘어가기 전에 답안을 선택해 주세요.');
      return;
    }
    setCurrentIndex((value) => Math.min(value + 1, (quiz?.questions.length ?? 1) - 1));
  };

  const handleSubmit = async () => {
    if (!quiz) return;
    const unanswered = quiz.questions.filter((question) => !answers[question.id]);
    if (unanswered.length > 0) {
      setValidationError(`미응답 문항 ${unanswered.length}개가 있습니다. 모든 문항에 답한 뒤 제출해 주세요.`);
      setCurrentIndex(quiz.questions.findIndex((question) => question.id === unanswered[0].id));
      return;
    }

    setSubmitting(true);
    try {
      const result = await submitQuiz(isMockMode, quiz.id, {
        answers: quiz.questions.map((question) => ({
          questionId: question.id,
          optionId: answers[question.id],
        })),
      });
      const enrichedResult: QuizSubmissionResult = {
        ...result,
        items: result.items.map((item) => {
          const question = quiz.questions.find((quizQuestion) => quizQuestion.id === item.questionId);
          const selectedOrder = item.selectedOptionOrder ?? Number(answers[item.questionId]);
          const chosenOption = question?.options.find((option) => Number(option.optionOrder ?? option.id) === selectedOrder);
          const correctOption = question?.options.find((option) => Number(option.optionOrder ?? option.id) === item.correctOptionOrder);

          return {
            ...item,
            question: item.question || question?.text || '',
            chosen: chosenOption?.text ?? item.chosen,
            correct: correctOption?.text ?? item.correct,
          };
        }),
      };
      navigate(`/books/${book.id}/quiz/result`, { state: { book, quiz, result: enrichedResult } satisfies ResultState });
    } catch (err) {
      setValidationError(err instanceof Error ? err.message : '답안 제출에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const setupPanel = (
    <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
      <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">Quiz Setup</div>
      <div className="font-display text-[24px] md:text-[30px] text-white font-bold mt-2">퀴즈 생성 조건</div>
      <div className="grid md:grid-cols-2 gap-4 mt-6">
        <label className="space-y-2">
          <span className="text-[11px] text-white/55">시작 챕터</span>
          <input
            type="number"
            min={1}
            max={maxChapter}
            value={options.chapterFrom}
            onChange={(event) => updateOptions({ chapterFrom: Number(event.target.value) })}
            className="w-full glass-soft rounded-xl px-4 py-3 text-sm text-white outline-none border border-white/10"
          />
        </label>
        <label className="space-y-2">
          <span className="text-[11px] text-white/55">종료 챕터</span>
          <input
            type="number"
            min={options.chapterFrom}
            max={maxChapter}
            value={options.chapterTo}
            onChange={(event) => updateOptions({ chapterTo: Number(event.target.value) })}
            className="w-full glass-soft rounded-xl px-4 py-3 text-sm text-white outline-none border border-white/10"
          />
        </label>
        <label className="space-y-2">
          <span className="text-[11px] text-white/55">문항 수</span>
          <select
            value={options.questionCount}
            onChange={(event) => updateOptions({ questionCount: Number(event.target.value) })}
            className="w-full glass-soft rounded-xl px-4 py-3 text-sm text-white outline-none border border-white/10 bg-[#15131A]"
          >
            {[3, 5, 10].map((count) => (
              <option key={count} value={count}>{count}문항</option>
            ))}
          </select>
        </label>
        <label className="space-y-2">
          <span className="text-[11px] text-white/55">난이도</span>
          <select
            value={options.difficulty}
            onChange={(event) => updateOptions({ difficulty: event.target.value as QuizDifficulty })}
            className="w-full glass-soft rounded-xl px-4 py-3 text-sm text-white outline-none border border-white/10 bg-[#15131A]"
          >
            {(Object.keys(DIFFICULTY_LABELS) as QuizDifficulty[]).map((difficulty) => (
              <option key={difficulty} value={difficulty}>{DIFFICULTY_LABELS[difficulty]}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="mt-6 flex flex-col sm:flex-row gap-3">
        <GlassBtn kind="accent" size="lg" onClick={startQuiz} className="inline-flex items-center justify-center gap-2">
          <Play className="w-4 h-4" /> 퀴즈 생성
        </GlassBtn>
        <GlassBtn kind="soft" size="lg" onClick={() => navigate(`/books/${book.id}`)}>
          상세로 돌아가기
        </GlassBtn>
      </div>
    </div>
  );

  const jobPanel = (
    <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
      <div className="flex items-center gap-3">
        {job?.status === 'FAILED' ? (
          <AlertCircle className="w-5 h-5 text-[#D46A6A]" />
        ) : (
          <Loader2 className="w-5 h-5 animate-spin text-[#7AA3D6]" />
        )}
        <div>
          <div className="font-display text-[24px] text-white font-bold">
            {job?.status === 'FAILED' ? '퀴즈 생성에 실패했습니다' : '퀴즈를 생성하고 있습니다'}
          </div>
          <div className="text-[12px] text-white/50 mt-1">
            {job?.message ?? '원문 문맥을 바탕으로 문제를 준비하는 중입니다.'}
          </div>
        </div>
      </div>
      <div className="mt-6 grid grid-cols-3 gap-2">
        {JOB_STEPS.map((step) => {
          const active = step === job?.status || (job?.status === 'PROCESSING' && step === 'QUEUED');
          return (
            <div key={step} className={`rounded-xl border px-3 py-3 ${active ? 'border-[#7AA3D6]/50 bg-[#7AA3D6]/10' : 'border-white/10 bg-white/[0.03]'}`}>
              <div className="text-[10px] font-mono text-white/45">{step}</div>
            </div>
          );
        })}
      </div>
      {job?.status === 'FAILED' && (
        <div className="mt-6 flex gap-3">
          <GlassBtn kind="primary" size="md" onClick={() => setPhase('setup')}>
            조건 다시 선택
          </GlassBtn>
          <GlassBtn kind="soft" size="md" onClick={startQuiz}>
            다시 요청
          </GlassBtn>
        </div>
      )}
    </div>
  );

  const solvingPanel = quiz && currentQuestion ? (
    <>
      <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider mb-2 text-white/70">
        <span>{currentIndex + 1} / {quiz.questions.length}</span>
        <span className="text-white/45">ANSWERED {selectedCount}</span>
      </div>
      <div className="h-[2px] bg-white/10 rounded-full overflow-hidden">
        <div className="h-full bg-white transition-[width] duration-300" style={{ width: `${progress}%` }} />
      </div>
      <div className="mt-5 glass rounded-2xl p-5 border border-white/5 relative z-10">
        <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">Q{currentIndex + 1}</div>
        <div className="font-display text-[20px] text-white leading-[1.4] mt-1.5 font-bold">{currentQuestion.text}</div>
        <div className="mt-4 space-y-2">
          {currentQuestion.options.map((option, idx) => {
            const checked = answers[currentQuestion.id] === option.id;
            return (
              <button
                key={option.id}
                onClick={() => pickAnswer(currentQuestion.id, option.id)}
                className={`w-full text-left flex items-center gap-3 px-3.5 py-3 rounded-xl border transition ${
                  checked ? 'border-[#7AA3D6] bg-[#7AA3D6]/10 text-white' : 'border-white/10 hover:border-white/25 text-white/90'
                }`}
              >
                <div className={`w-6 h-6 rounded-full grid place-items-center text-[10px] font-mono shrink-0 ${checked ? 'bg-[#7AA3D6] text-white' : 'glass-soft text-white/70'}`}>
                  {String.fromCodePoint(65 + idx)}
                </div>
                <div className="text-[12px] flex-1">{option.text}</div>
              </button>
            );
          })}
        </div>
        {validationError && (
          <div className="mt-4 rounded-xl p-3.5 glass-soft border-l-2 border-[#D46A6A] text-[12px] text-white/75">
            {validationError}
          </div>
        )}
      </div>
      <div className="mt-6 flex items-center justify-between gap-3">
        <GlassBtn kind="soft" size="md" onClick={() => setCurrentIndex((value) => Math.max(0, value - 1))} disabled={currentIndex === 0}>
          이전
        </GlassBtn>
        {currentIndex === quiz.questions.length - 1 ? (
          <GlassBtn kind="primary" size="md" onClick={handleSubmit} disabled={submitting} className="flex items-center gap-1.5">
            {submitting ? '제출 중...' : '답안 제출'} <DArrow className="w-3.5 h-3.5" />
          </GlassBtn>
        ) : (
          <GlassBtn kind="primary" size="md" onClick={goNext} className="flex items-center gap-1.5">
            다음 문제 <DArrow className="w-3.5 h-3.5" />
          </GlassBtn>
        )}
      </div>
    </>
  ) : null;

  const content = phase === 'setup' ? setupPanel : phase === 'job' ? jobPanel : solvingPanel;
  const subtitle = phase === 'setup' ? 'Quiz Setup' : phase === 'job' ? 'Generating' : `${quiz?.questions.length ?? 0} Questions`;

  return (
    <QuizLayout
      book={book}
      subtitle={subtitle}
      activeTab="quiz"
      mobileContent={
        <div>
          <div className="font-display text-[22px] font-bold text-white mb-0.5 leading-tight">{book.title}</div>
          <div className="text-[10px] text-white/45 font-mono uppercase tracking-wider mb-5">{subtitle}</div>
          {content}
        </div>
      }
      desktopContent={
        <>
          <button onClick={() => navigate(`/books/${book.id}`)} className="text-[11px] font-mono uppercase tracking-[0.14em] text-white/45 hover:text-white flex items-center gap-1 transition">
            <DBack className="w-3.5 h-3.5" /> BACK TO DETAIL
          </button>
          <div className="grid grid-cols-[minmax(0,1fr)_300px] gap-10 mt-6">
            <div>
              <div className="font-mono text-[10px] uppercase tracking-[0.22em] text-white/45">COMPREHENSION QUIZ</div>
              <div className="font-display text-[44px] text-white leading-[1] mt-2 font-bold">{book.title}</div>
              <div className="text-[12px] text-white/45 mt-1 font-mono uppercase tracking-wider mb-8">{subtitle}</div>
              <div className="max-w-[680px]">{content}</div>
            </div>
            <aside className="space-y-3">
              <div className="glass rounded-2xl p-5 border border-white/5">
                <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">퀴즈 진행률</div>
                <div className="flex items-end gap-1 mt-2">
                  <div className="font-display text-[44px] leading-none font-bold">{progress}</div>
                  <div className="text-[10px] text-white/45 mb-1.5 font-mono">%</div>
                </div>
                <div className="mt-3 h-[2px] bg-white/10 rounded-full overflow-hidden">
                  <div className="h-full bg-white" style={{ width: `${progress}%` }} />
                </div>
              </div>
            </aside>
          </div>
        </>
      }
    />
  );
};

export const QuizResultPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { isMockMode } = useAuth();
  const state = location.state as ResultState | null;
  const [history, setHistory] = useState<QuizHistoryItem[]>([]);
  const [historyError, setHistoryError] = useState<string | null>(null);

  useEffect(() => {
    if (!state) {
      navigate(bookId ? `/books/${bookId}/quiz` : '/', { replace: true });
    }
  }, [bookId, navigate, state]);

  useEffect(() => {
    getMyQuizHistory(isMockMode)
      .then(setHistory)
      .catch((err) => setHistoryError(err instanceof Error ? err.message : '퀴즈 히스토리를 불러오지 못했습니다.'));
  }, [isMockMode]);

  if (!state) return <LoadingScreen />;

  const { book, quiz, result } = state;
  const titleText = result.score >= Math.ceil(result.total * 0.8) ? '훌륭해요!' : result.score >= Math.ceil(result.total * 0.6) ? '잘 했어요' : '다시 도전!';
  const historyItems = history.slice(0, 3);

  const breakdown = (
    <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
      <div className="font-display text-[16px] mb-4 font-bold">문항별 채점 결과</div>
      <div className="space-y-4">
        {result.items.map((item, idx) => (
          <div key={item.questionId} className="border-b border-white/5 pb-4 last:border-0 last:pb-0">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-2">
                <span className={`w-5 h-5 rounded-full grid place-items-center text-[10px] font-mono font-bold shrink-0 mt-0.5 ${item.isCorrect ? 'bg-[#5FC9A0]' : 'bg-[#D46A6A]'} text-white`}>
                  {item.isCorrect ? <DCheck className="w-3 h-3" /> : <DXIcon className="w-3 h-3" />}
                </span>
                <div className="text-[12px] font-medium leading-snug">{idx + 1}. {item.question}</div>
              </div>
              <span className={`text-[10px] font-bold shrink-0 ${item.isCorrect ? 'text-[#5FC9A0]' : 'text-[#D46A6A]'}`}>
                {item.isCorrect ? '정답' : '오답'}
              </span>
            </div>
            <div className="mt-2 pl-7 text-[11px] text-white/55 space-y-1 font-light">
              <div><span className="text-white/30 mr-1">내 답:</span>{item.chosen}</div>
              {!item.isCorrect && <div><span className="text-white/30 mr-1">정답:</span>{item.correct}</div>}
              <div className="text-white/70 leading-relaxed">{item.explanation}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  const historyPanel = (
    <div className="glass rounded-2xl p-5 border border-white/5">
      <div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">
        <History className="w-3.5 h-3.5" /> Quiz History
      </div>
      {historyError ? (
        <p className="text-[12px] text-[#D46A6A] mt-3">{historyError}</p>
      ) : historyItems.length === 0 ? (
        <p className="text-[12px] text-white/50 mt-3">아직 저장된 퀴즈 기록이 없습니다.</p>
      ) : (
        <div className="mt-3 space-y-2">
          {historyItems.map((item) => (
            <button
              key={`${item.quizId}-${item.submittedAt}`}
              onClick={() => navigate(`/books/${item.bookId}`)}
              className="w-full text-left glass-soft rounded-xl px-3 py-3 border border-white/5 hover:border-white/15 transition"
            >
              <div className="text-[12px] text-white/85 truncate">{item.bookTitle}</div>
              <div className="text-[10px] text-white/40 mt-1">{item.score}/{item.total} · 정답률 {item.accuracy}%</div>
            </button>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <QuizLayout
      book={book}
      subtitle="Quiz Report"
      activeTab="me"
      mobileContent={
        <div>
          <div className="glass rounded-2xl p-5 mt-2 flex items-center gap-5 border border-white/5">
            <div className="relative shrink-0">
              <ScoreRing score={result.score} total={result.total} size={88} />
              <div className="absolute inset-0 grid place-items-center">
                <div className="text-center">
                  <div className="font-display text-[26px] leading-none text-white font-bold">{result.score}</div>
                  <div className="text-[10px] text-white/40 font-mono">/ {result.total}</div>
                </div>
              </div>
            </div>
            <div className="min-w-0">
              <div className="font-display text-[22px] leading-[1.1] font-bold text-white">{titleText}</div>
              <div className="text-[12px] text-white/55 mt-1.5 font-light">{book.title} · {quiz.chapterRange} · 정답률 {result.accuracy}%</div>
              <div className="text-[10px] font-mono text-white/35 mt-1">{DIFFICULTY_LABELS[quiz.difficulty]}</div>
            </div>
          </div>
          <div className="mt-4">{breakdown}</div>
          <div className="mt-4">{historyPanel}</div>
          <div className="flex gap-2 mt-4 z-10 relative">
            <button onClick={() => navigate(`/books/${book.id}/quiz`)} className="flex-1 py-3 glass-soft rounded-full text-[12px] text-white/75 text-center active:scale-[0.98] transition">
              다시 풀기
            </button>
            <button onClick={() => navigate(`/books/${book.id}`)} className="flex-1 py-3 rounded-full text-[12px] text-white font-medium text-center active:scale-[0.98] transition" style={{ background: 'linear-gradient(135deg,#7AA3D6,#3E6FA9)' }}>
              상세로 돌아가기
            </button>
          </div>
        </div>
      }
      desktopContent={
        <>
          <button onClick={() => navigate(`/books/${book.id}/quiz`)} className="text-[10px] font-mono uppercase tracking-[0.14em] text-white/35 hover:text-white flex items-center gap-1 transition mb-6">
            <RotateCcw className="w-3 h-3" /> RESTART QUIZ
          </button>
          <div className="grid grid-cols-[minmax(0,1fr)_360px] gap-8">
            <div className="space-y-5">
              <div className="glass rounded-2xl p-7 flex items-center gap-8 border border-white/5">
                <div className="relative shrink-0">
                  <ScoreRing score={result.score} total={result.total} size={120} />
                  <div className="absolute inset-0 grid place-items-center">
                    <div className="text-center">
                      <div className="font-display text-[32px] leading-none text-white font-bold">{result.score}</div>
                      <div className="text-[12px] text-white/40 font-mono">/ {result.total}</div>
                    </div>
                  </div>
                </div>
                <div>
                  <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">COMPREHENSION SCORE</div>
                  <div className="font-display text-[32px] text-white leading-none mt-2.5 font-bold">{titleText}</div>
                  <div className="text-[13px] text-white/55 mt-2.5 font-light">
                    도서 <span className="font-semibold text-white">『{book.title}』</span> 퀴즈 결과 정답률 <span className="font-semibold text-white">{result.accuracy}%</span>를 기록했습니다.
                  </div>
                </div>
              </div>
              {breakdown}
            </div>
            <aside className="space-y-4">
              <div className="glass rounded-2xl p-5 border border-white/5">
                <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">학습 제안</div>
                <div className="text-[18px] font-display mt-2 font-semibold">
                  {result.accuracy >= 80 ? '다음 챕터로 진행하세요!' : '해당 챕터를 복습해 보세요.'}
                </div>
                <p className="text-[12px] text-white/55 mt-1.5 font-light leading-relaxed">
                  {result.accuracy >= 80
                    ? '이해도가 높습니다. 다음 챕터의 AI 요약이나 장면 일러스트를 이어서 확인해도 좋습니다.'
                    : '오답 해설을 확인하고 해당 챕터의 요약본을 다시 읽어보는 것을 권장합니다.'}
                </p>
              </div>
              {historyPanel}
            </aside>
          </div>
        </>
      }
    />
  );
};
