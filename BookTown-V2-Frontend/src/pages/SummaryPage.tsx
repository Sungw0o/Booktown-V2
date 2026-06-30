import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBookById } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import {
  createSummary,
  getSummaries,
  getSummary,
  getSummaryJob,
  regenerateSummary,
  saveFeedback,
} from '../api/summaryApi';
import type { Summary, SummaryJob, SummaryJobStatus } from '../api/summaryApi';
import { DBack, DkTopNav } from '../components/Primitives';
import {
  AlertCircle,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  Clock,
  FileText,
  Loader2,
  RefreshCw,
  Sparkles,
  Star,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';

// ─── Job Status Badge ────────────────────────────────────────────────────────

const JOB_STATUS_MAP: Record<
  SummaryJobStatus,
  { label: string; cls: string }
> = {
  QUEUED: { label: '대기 중', cls: 'bg-amber-500/10 text-amber-500' },
  PROCESSING: { label: '생성 중', cls: 'bg-blue-500/10 text-blue-500' },
  COMPLETED: { label: '완료', cls: 'bg-emerald-500/10 text-emerald-500' },
  FAILED: { label: '실패', cls: 'bg-red-500/10 text-red-500' },
};

const JobStatusBadge: React.FC<{ status: SummaryJobStatus }> = ({ status }) => {
  const { label, cls } = JOB_STATUS_MAP[status];
  return (
    <span
      className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full ${cls}`}
    >
      {(status === 'QUEUED' || status === 'PROCESSING') && (
        <Loader2 className="w-3 h-3 animate-spin" />
      )}
      {label}
    </span>
  );
};

// ─── Star Rating ─────────────────────────────────────────────────────────────

const StarRating: React.FC<{
  value: number;
  onChange: (v: number) => void;
  readonly?: boolean;
}> = ({ value, onChange, readonly }) => (
  <div className="flex gap-1">
    {[1, 2, 3, 4, 5].map((n) => (
      <button
        key={n}
        id={`star-${n}`}
        onClick={() => !readonly && onChange(n)}
        disabled={readonly}
        className="transition-transform disabled:cursor-default"
      >
        <Star
          className={`w-5 h-5 transition-colors ${
            n <= value
              ? 'fill-amber-400 text-amber-400'
              : 'text-slate-300 dark:text-white/15'
          } ${!readonly ? 'hover:fill-amber-300 hover:text-amber-300 hover:scale-110' : ''}`}
        />
      </button>
    ))}
  </div>
);

// ─── Summary Card (list item) ────────────────────────────────────────────────

const SummaryCard: React.FC<{
  summary: Summary;
  isSelected: boolean;
  onClick: () => void;
}> = ({ summary, isSelected, onClick }) => {
  const date = new Date(summary.createdAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <button
      id={`summary-card-${summary.summaryId}`}
      onClick={onClick}
      className={`w-full text-left p-4 rounded-2xl transition-all duration-200 border ${
        isSelected
          ? 'border-purple-500/40 bg-purple-500/5'
          : 'border-black/5 dark:border-white/5 glass-soft hover:border-purple-400/20'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5">
            {summary.isRegeneration && (
              <span className="text-[9px] font-mono font-bold px-1.5 py-0.5 rounded-full bg-violet-500/10 text-violet-400">
                재생성
              </span>
            )}
            {summary.feedback && (
              <StarRating value={summary.feedback.rating} onChange={() => {}} readonly />
            )}
          </div>
          <p className="text-xs text-slate-600 dark:text-white/60 line-clamp-2 leading-relaxed">
            {summary.content.replace(/#{1,3}\s/g, '').slice(0, 100)}...
          </p>
          <div className="flex items-center gap-1 mt-2">
            <Clock className="w-3 h-3 text-slate-400 dark:text-white/30" />
            <span className="text-[10px] text-slate-400 dark:text-white/30">{date}</span>
          </div>
        </div>
        <ChevronRight
          className={`w-4 h-4 shrink-0 transition-colors mt-1 ${
            isSelected ? 'text-purple-500' : 'text-slate-400 dark:text-white/25'
          }`}
        />
      </div>
    </button>
  );
};

// ─── Summary Detail ──────────────────────────────────────────────────────────

const SummaryDetail: React.FC<{
  summary: Summary;
  onRegenerate: () => void;
  onFeedback: (rating: number, comment?: string) => Promise<void>;
  isRegenerating: boolean;
}> = ({ summary, onRegenerate, onFeedback, isRegenerating }) => {
  const [feedbackRating, setFeedbackRating] = useState(
    summary.feedback?.rating ?? 0,
  );
  const [feedbackComment, setFeedbackComment] = useState(
    summary.feedback?.comment ?? '',
  );
  const [feedbackSaving, setFeedbackSaving] = useState(false);
  const [feedbackDone, setFeedbackDone] = useState(!!summary.feedback);

  const handleFeedbackSubmit = async () => {
    if (feedbackRating === 0) return;
    setFeedbackSaving(true);
    try {
      await onFeedback(feedbackRating, feedbackComment || undefined);
      setFeedbackDone(true);
    } finally {
      setFeedbackSaving(false);
    }
  };

  // Render markdown-like content
  const renderContent = (content: string) => {
    const lines = content.split('\n');
    return lines.map((line, i) => {
      if (line.startsWith('## ')) {
        return (
          <h2
            key={i}
            className="font-serif text-xl font-bold text-slate-800 dark:text-white mt-6 mb-3 first:mt-0"
          >
            {line.slice(3)}
          </h2>
        );
      }
      if (line.startsWith('### ')) {
        return (
          <h3
            key={i}
            className="font-semibold text-sm text-slate-700 dark:text-white/80 mt-4 mb-2"
          >
            {line.slice(4)}
          </h3>
        );
      }
      if (line.startsWith('- **')) {
        const match = line.match(/^- \*\*(.+?)\*\*: (.+)/);
        if (match) {
          return (
            <p key={i} className="text-sm text-slate-600 dark:text-white/65 mb-1.5 pl-3">
              <span className="font-semibold text-slate-800 dark:text-white">
                {match[1]}
              </span>
              : {match[2]}
            </p>
          );
        }
      }
      if (line.startsWith('- ')) {
        return (
          <p key={i} className="text-sm text-slate-600 dark:text-white/65 mb-1.5 pl-3">
            • {line.slice(2)}
          </p>
        );
      }
      if (line.startsWith('> ')) {
        return (
          <p
            key={i}
            className="text-xs text-slate-500 dark:text-white/40 italic border-l-2 border-purple-400/30 pl-3 my-2"
          >
            {line.slice(2)}
          </p>
        );
      }
      if (line.trim() === '') return <div key={i} className="h-1" />;
      return (
        <p key={i} className="text-sm text-slate-600 dark:text-white/65 leading-relaxed mb-2">
          {line}
        </p>
      );
    });
  };

  return (
    <div className="flex flex-col gap-5">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {summary.isRegeneration && (
            <span className="text-[9px] font-mono px-2 py-0.5 rounded-full bg-violet-500/10 text-violet-400 font-bold">
              재생성
            </span>
          )}
          <span className="text-[10px] text-slate-400 dark:text-white/30">
            {new Date(summary.createdAt).toLocaleDateString('ko-KR', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </span>
        </div>
        <button
          id="regenerate-btn"
          onClick={onRegenerate}
          disabled={isRegenerating}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl glass-soft text-xs font-semibold text-slate-600 dark:text-white/70 hover:text-purple-600 dark:hover:text-purple-300 disabled:opacity-40 disabled:cursor-not-allowed transition"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isRegenerating ? 'animate-spin' : ''}`} />
          재생성
        </button>
      </div>

      {/* Content */}
      <div className="glass rounded-2xl p-6 leading-relaxed">
        {renderContent(summary.content)}
      </div>

      {/* Feedback */}
      <div className="glass rounded-2xl p-5">
        <div className="flex items-center gap-2 mb-4">
          {feedbackDone ? (
            <ThumbsUp className="w-4 h-4 text-emerald-500" />
          ) : (
            <ThumbsDown className="w-4 h-4 text-slate-400 dark:text-white/30" />
          )}
          <h3 className="text-sm font-semibold text-slate-700 dark:text-white/80">
            {feedbackDone ? '피드백 완료' : '이 요약이 도움이 되었나요?'}
          </h3>
        </div>

        {feedbackDone ? (
          <div className="flex items-center gap-3">
            <StarRating value={feedbackRating} onChange={() => {}} readonly />
            {feedbackComment && (
              <p className="text-xs text-slate-500 dark:text-white/40 italic">
                "{feedbackComment}"
              </p>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <StarRating value={feedbackRating} onChange={setFeedbackRating} />
            <textarea
              placeholder="추가 의견을 남겨주세요 (선택)"
              value={feedbackComment}
              onChange={(e) => setFeedbackComment(e.target.value)}
              rows={2}
              className="w-full text-xs text-slate-700 dark:text-white/80 bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 rounded-xl px-3 py-2 resize-none focus:outline-none focus:border-purple-400/40 placeholder-slate-400 dark:placeholder-white/25 transition"
            />
            <button
              id="feedback-submit-btn"
              onClick={handleFeedbackSubmit}
              disabled={feedbackRating === 0 || feedbackSaving}
              className="self-start inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 disabled:bg-purple-600/30 disabled:cursor-not-allowed text-white text-xs font-semibold transition"
            >
              {feedbackSaving ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <CheckCircle2 className="w-3.5 h-3.5" />
              )}
              피드백 저장
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

// ─── Main Page ────────────────────────────────────────────────────────────────

export const SummaryPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { user, logout, isMockMode } = useAuth();
  const navigate = useNavigate();

  // Book info
  const [book, setBook] = useState<Book | null>(null);

  // Summary list
  const [summaries, setSummaries] = useState<Summary[]>([]);
  const [listLoading, setListLoading] = useState(true);

  // Selected summary detail
  const [selectedSummaryId, setSelectedSummaryId] = useState<string | null>(null);
  const [selectedSummary, setSelectedSummary] = useState<Summary | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Active job
  const [activeJob, setActiveJob] = useState<SummaryJob | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Scope selection
  const [scope, setScope] = useState<'full' | 'chapter'>('full');

  // Request state
  const [isRequesting, setIsRequesting] = useState(false);
  const [isRegenerating, setIsRegenerating] = useState(false);

  // Toast
  const [toast, setToast] = useState<{
    message: string;
    type: 'success' | 'error' | 'info';
  } | null>(null);

  // Error state (409/429/502)
  const [requestError, setRequestError] = useState<string | null>(null);

  const showToast = (
    message: string,
    type: 'success' | 'error' | 'info' = 'success',
  ) => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  // ── Load book + summary list ─────────────────────────────────────────────────
  useEffect(() => {
    if (!bookId) return;
    let cancelled = false;

    const init = async () => {
      try {
        const [bookData, summaryList] = await Promise.all([
          getBookById(isMockMode, bookId),
          getSummaries(isMockMode, bookId),
        ]);
        if (cancelled) return;
        setBook(bookData);
        setSummaries(summaryList);
        if (summaryList.length > 0) {
          setSelectedSummaryId(summaryList[0].summaryId);
        }
      } catch {
        // silent
      } finally {
        if (!cancelled) setListLoading(false);
      }
    };

    init();
    return () => { cancelled = true; };
  }, [bookId, isMockMode]);

  // ── Load selected summary detail ─────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;

    const fetchDetail = async () => {
      if (!selectedSummaryId) {
        if (!cancelled) {
          setSelectedSummary(null);
          setDetailLoading(false);
        }
        return;
      }

      if (!cancelled) setDetailLoading(true);
      try {
        const data = await getSummary(isMockMode, selectedSummaryId);
        if (!cancelled) setSelectedSummary(data);
      } catch {
        if (!cancelled) setSelectedSummary(null);
      } finally {
        if (!cancelled) setDetailLoading(false);
      }
    };

    fetchDetail();
    return () => { cancelled = true; };
  }, [selectedSummaryId, isMockMode]);

  // ── Polling ──────────────────────────────────────────────────────────────────
  const stopPolling = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  };

  const startPolling = (job: SummaryJob) => {
    stopPolling();
    pollingRef.current = setInterval(async () => {
      try {
        const updated = await getSummaryJob(isMockMode, job.jobId);
        setActiveJob(updated);

        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          stopPolling();

          if (updated.status === 'COMPLETED') {
            showToast('요약이 완성되었습니다! ✨', 'success');
            // Refresh list and auto-select new summary
            if (bookId) {
              const newList = await getSummaries(isMockMode, bookId);
              setSummaries(newList);
              if (updated.summaryId) setSelectedSummaryId(updated.summaryId);
            }
          } else {
            showToast(
              updated.errorMessage ?? '요약 생성에 실패했습니다.',
              'error',
            );
          }

          setTimeout(() => setActiveJob(null), 2000);
        }
      } catch {
        // silent
      }
    }, 3000);
  };

  useEffect(() => () => stopPolling(), []);

  // ── Create summary ────────────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!bookId || isRequesting) return;
    setIsRequesting(true);
    setRequestError(null);

    try {
      const job = await createSummary(
        isMockMode,
        bookId,
        scope === 'chapter' ? 'chapter' : undefined,
      );
      setActiveJob(job);
      startPolling(job);
      showToast('요약 생성을 요청했습니다.', 'info');
    } catch (err: unknown) {
      const e = err as Error & { response?: { status: number } };
      const status = e.response?.status;
      if (status === 409) {
        setRequestError('이미 진행 중인 요약이 있습니다. 완료 후 다시 시도해 주세요.');
      } else if (status === 429) {
        setRequestError('요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.');
      } else if (status === 502) {
        setRequestError('AI 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.');
      } else {
        setRequestError('요약 생성 요청에 실패했습니다.');
      }
    } finally {
      setIsRequesting(false);
    }
  };

  // ── Regenerate ────────────────────────────────────────────────────────────────
  const handleRegenerate = async () => {
    if (!selectedSummary || !bookId || isRegenerating) return;
    setIsRegenerating(true);
    try {
      const job = await regenerateSummary(
        isMockMode,
        selectedSummary.summaryId,
        Number(bookId),
      );
      setActiveJob(job);
      startPolling(job);
      showToast('재생성을 요청했습니다.', 'info');
    } catch {
      showToast('재생성 요청에 실패했습니다.', 'error');
    } finally {
      setIsRegenerating(false);
    }
  };

  // ── Feedback ──────────────────────────────────────────────────────────────────
  const handleFeedback = async (rating: number, comment?: string) => {
    if (!selectedSummary) return;
    await saveFeedback(isMockMode, selectedSummary.summaryId, rating, comment);
    // Update local list
    setSummaries((prev) =>
      prev.map((s) =>
        s.summaryId === selectedSummary.summaryId
          ? { ...s, feedback: { rating, comment: comment ?? null } }
          : s,
      ),
    );
    showToast('피드백이 저장되었습니다.', 'success');
  };

  const isJobActive =
    activeJob?.status === 'QUEUED' || activeJob?.status === 'PROCESSING';

  // ─── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen dk-surface flex flex-col selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain absolute inset-0 opacity-40 pointer-events-none" />
      <div className="absolute top-[5%] right-[10%] w-[500px] h-[500px] rounded-full bg-blue-600/5 dark:bg-blue-900/10 blur-[160px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[5%] w-[400px] h-[400px] rounded-full bg-purple-600/5 dark:bg-purple-900/8 blur-[140px] pointer-events-none" />

      <DkTopNav
        active="home"
        go={(tab) => {
          if (tab === 'home' || tab === 'search' || tab === 'history') navigate('/');
          else if (tab === 'me') navigate('/me');
          else if (tab === 'admin') navigate('/admin');
        }}
        onLogout={logout}
        nickname={user?.nickname || '민'}
      />

      <div className="w-full max-w-5xl mx-auto px-4 pt-24 pb-16 z-10 flex flex-col gap-6">
        {/* Back & Title */}
        <div>
          <button
            id="back-btn"
            onClick={() => navigate(bookId ? `/books/${bookId}` : '/')}
            className="inline-flex items-center gap-1.5 text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white text-xs font-semibold mb-5 transition"
          >
            <DBack className="w-4 h-4" />
            도서 상세로
          </button>

          <div>
            {book && (
              <p className="text-xs font-mono text-blue-500/70 dark:text-blue-400/60 mb-1">
                {book.author} · {book.genre}
              </p>
            )}
            <h1 className="font-serif text-2xl md:text-3xl font-bold text-slate-800 dark:text-white">
              {book?.title ?? 'AI 요약'}
            </h1>
            <p className="text-xs text-slate-500 dark:text-white/40 mt-1">
              RAG 기반 AI가 생성한 맞춤형 요약
            </p>
          </div>
        </div>

        {/* Generate Panel */}
        <div className="glass rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-4 h-4 text-blue-500" />
            <h2 className="text-sm font-semibold text-slate-700 dark:text-white/80">
              새 요약 생성
            </h2>
          </div>

          {/* Scope selector */}
          <div className="flex gap-2 mb-4">
            {(['full', 'chapter'] as const).map((s) => (
              <button
                key={s}
                id={`scope-${s}`}
                onClick={() => setScope(s)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition border ${
                  scope === s
                    ? 'bg-blue-500/10 border-blue-500/30 text-blue-600 dark:text-blue-300'
                    : 'border-black/5 dark:border-white/5 glass-soft text-slate-500 dark:text-white/50 hover:border-blue-400/20'
                }`}
              >
                {s === 'full' ? '📖 전체 줄거리' : '📑 챕터별'}
              </button>
            ))}
          </div>

          {/* Error message */}
          {requestError && (
            <div className="flex items-start gap-2 px-3 py-2.5 rounded-xl bg-red-500/10 border border-red-500/15 mb-4">
              <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
              <p className="text-xs text-red-400">{requestError}</p>
              <button
                onClick={() => setRequestError(null)}
                className="ml-auto"
              >
                <X className="w-3.5 h-3.5 text-red-400/60 hover:text-red-400 transition" />
              </button>
            </div>
          )}

          {/* Active job status */}
          {activeJob && (
            <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-blue-500/5 border border-blue-500/10 mb-4">
              <JobStatusBadge status={activeJob.status} />
              <p className="text-xs text-slate-500 dark:text-white/40">
                {activeJob.status === 'QUEUED' && 'AI가 요약을 준비하고 있습니다...'}
                {activeJob.status === 'PROCESSING' && 'RAG 분석 및 요약 생성 중...'}
                {activeJob.status === 'COMPLETED' && '요약이 완성되었습니다!'}
                {activeJob.status === 'FAILED' && (activeJob.errorMessage ?? '생성에 실패했습니다.')}
              </p>
            </div>
          )}

          <button
            id="create-summary-btn"
            onClick={handleCreate}
            disabled={isRequesting || isJobActive}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:bg-blue-600/30 disabled:cursor-not-allowed text-white text-xs font-semibold transition-all duration-200"
          >
            {isRequesting ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4" />
            )}
            {isRequesting ? '요청 중...' : isJobActive ? '생성 중...' : '요약 생성하기'}
          </button>
        </div>

        {/* Content area: list + detail */}
        {listLoading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3">
            <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
            <p className="text-xs text-slate-400 dark:text-white/30">불러오는 중...</p>
          </div>
        ) : summaries.length === 0 ? (
          <div className="glass rounded-2xl p-12 flex flex-col items-center gap-3">
            <FileText className="w-10 h-10 text-slate-300 dark:text-white/15" />
            <p className="text-sm text-slate-500 dark:text-white/40">
              아직 생성된 요약이 없습니다.
            </p>
            <p className="text-xs text-slate-400 dark:text-white/25">
              위에서 요약을 생성해 보세요.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-[280px_1fr] gap-4 items-start">
            {/* Summary list */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2 mb-1 px-1">
                <BookOpen className="w-3.5 h-3.5 text-blue-500" />
                <span className="text-xs font-semibold text-slate-600 dark:text-white/60">
                  저장된 요약 ({summaries.length})
                </span>
              </div>
              {summaries.map((s) => (
                <SummaryCard
                  key={s.summaryId}
                  summary={s}
                  isSelected={selectedSummaryId === s.summaryId}
                  onClick={() => setSelectedSummaryId(s.summaryId)}
                />
              ))}
            </div>

            {/* Summary detail */}
            <div>
              {detailLoading ? (
                <div className="flex items-center justify-center py-20">
                  <Loader2 className="w-6 h-6 text-blue-500 animate-spin" />
                </div>
              ) : selectedSummary ? (
                <SummaryDetail
                  summary={selectedSummary}
                  onRegenerate={handleRegenerate}
                  onFeedback={handleFeedback}
                  isRegenerating={isRegenerating || isJobActive}
                />
              ) : (
                <div className="glass rounded-2xl p-10 flex items-center justify-center">
                  <p className="text-sm text-slate-400 dark:text-white/30">
                    왼쪽 목록에서 요약을 선택하세요.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Toast */}
      {toast && (
        <div
          className={`
            fixed bottom-6 left-1/2 -translate-x-1/2 z-50
            flex items-center gap-2 px-4 py-2.5 rounded-full shadow-lg
            text-xs font-semibold backdrop-blur-md
            transition-all duration-300
            ${
              toast.type === 'success'
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/20'
                : toast.type === 'error'
                ? 'bg-red-500/20 text-red-400 border border-red-500/20'
                : 'bg-blue-500/20 text-blue-400 border border-blue-500/20'
            }
          `}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-3.5 h-3.5" />
          ) : toast.type === 'error' ? (
            <AlertCircle className="w-3.5 h-3.5" />
          ) : (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          )}
          {toast.message}
        </div>
      )}
    </div>
  );
};

export default SummaryPage;
