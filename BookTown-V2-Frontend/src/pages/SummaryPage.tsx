import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBookById } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import {
  createSummary,
  getSummaryJob,
  getSummaryDetail,
  getSummaryList,
  regenerateSummary,
  sendFeedback,
  LEVEL_META,
  LENGTH_META,
} from '../api/summaryApi';
import type {
  Summary,
  SummaryJob,
  SummaryJobStatus,
  SummaryLevel,
  SummaryLength,
} from '../api/summaryApi';
import { DBack, DkTopNav } from '../components/Primitives';
import {
  AlertCircle,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Loader2,
  RefreshCw,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
} from 'lucide-react';

// ─── Sub-components ──────────────────────────────────────────────────────────

interface OptionPickerProps<T extends string> {
  label: string;
  options: Record<T, { label: string; desc: string }>;
  selected: T;
  onChange: (v: T) => void;
}

function OptionPicker<T extends string>({
  label,
  options,
  selected,
  onChange,
}: OptionPickerProps<T>) {
  const keys = Object.keys(options) as T[];
  return (
    <div>
      <p className="text-[10px] font-mono text-slate-400 dark:text-white/40 uppercase tracking-wider mb-2">
        {label}
      </p>
      <div className="flex gap-2">
        {keys.map((key) => {
          const meta = options[key];
          const isSelected = key === selected;
          return (
            <button
              key={key}
              onClick={() => onChange(key)}
              className={`flex-1 rounded-xl py-2.5 px-3 text-left transition-all border text-xs ${
                isSelected
                  ? 'border-purple-500/60 bg-purple-500/10 text-purple-600 dark:text-purple-300'
                  : 'border-black/5 dark:border-white/5 glass-soft text-slate-600 dark:text-white/70 hover:border-purple-400/30'
              }`}
            >
              <p className="font-semibold leading-tight">{meta.label}</p>
              <p className="text-[10px] mt-0.5 opacity-70 leading-tight">{meta.desc}</p>
            </button>
          );
        })}
      </div>
    </div>
  );
}

interface JobStatusBadgeProps {
  status: SummaryJobStatus;
}

const JobStatusBadge: React.FC<JobStatusBadgeProps> = ({ status }) => {
  const map: Record<SummaryJobStatus, { label: string; cls: string }> = {
    QUEUED: { label: '대기 중', cls: 'bg-amber-500/10 text-amber-500' },
    PROCESSING: { label: 'AI 생성 중', cls: 'bg-blue-500/10 text-blue-500' },
    COMPLETED: { label: '완료', cls: 'bg-emerald-500/10 text-emerald-500' },
    FAILED: { label: '실패', cls: 'bg-red-500/10 text-red-500' },
  };
  const { label, cls } = map[status];
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-semibold ${cls}`}>
      {(status === 'QUEUED' || status === 'PROCESSING') && (
        <Loader2 className="w-3 h-3 animate-spin" />
      )}
      {label}
    </span>
  );
};

interface SummaryCardProps {
  summary: Summary;
  isMockMode: boolean;
  onRegenerate: (summaryId: string) => void;
  regenerating: boolean;
}

const SummaryCard: React.FC<SummaryCardProps> = ({
  summary,
  isMockMode,
  onRegenerate,
  regenerating,
}) => {
  const [localHelpful, setLocalHelpful] = useState<boolean | null>(summary.helpful);
  const [feedbackSending, setFeedbackSending] = useState(false);
  const [expanded, setExpanded] = useState(true);

  const handleFeedback = async (helpful: boolean) => {
    if (feedbackSending || localHelpful !== null) return;
    setFeedbackSending(true);
    try {
      await sendFeedback(isMockMode, summary.summaryId, helpful);
      setLocalHelpful(helpful);
    } catch {
      // 피드백 실패는 조용히 무시
    } finally {
      setFeedbackSending(false);
    }
  };

  return (
    <div className="glass rounded-2xl overflow-hidden">
      {/* Header */}
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center justify-between p-5 text-left hover:bg-black/[0.02] dark:hover:bg-white/[0.02] transition"
      >
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-blue-500/10 flex items-center justify-center flex-shrink-0">
            <BookOpen className="w-4 h-4 text-blue-500" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-800 dark:text-white">
              Chapter {summary.chapterId} · {LEVEL_META[summary.level].label} / {LENGTH_META[summary.length].label}
            </p>
            <p className="text-[10px] text-slate-400 dark:text-white/40 mt-0.5">
              {new Date(summary.createdAt).toLocaleDateString('ko-KR', {
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </p>
          </div>
        </div>
        {expanded ? (
          <ChevronUp className="w-4 h-4 text-slate-400 flex-shrink-0" />
        ) : (
          <ChevronDown className="w-4 h-4 text-slate-400 flex-shrink-0" />
        )}
      </button>

      {expanded && (
        <div className="px-5 pb-5 border-t border-black/5 dark:border-white/5">
          {/* Content */}
          <p className="text-sm text-slate-700 dark:text-white/80 leading-relaxed mt-4 whitespace-pre-line">
            {summary.content}
          </p>

          {/* Key Points */}
          {summary.keyPoints.length > 0 && (
            <div className="mt-5">
              <p className="text-[10px] font-mono text-slate-400 dark:text-white/40 uppercase tracking-wider mb-2">
                핵심 포인트
              </p>
              <ul className="space-y-1.5">
                {summary.keyPoints.map((point, i) => (
                  <li key={i} className="flex items-start gap-2 text-xs text-slate-600 dark:text-white/70">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 flex-shrink-0 mt-0.5" />
                    {point}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Characters */}
          {summary.characters.length > 0 && (
            <div className="mt-4">
              <p className="text-[10px] font-mono text-slate-400 dark:text-white/40 uppercase tracking-wider mb-2">
                등장인물
              </p>
              <div className="flex flex-wrap gap-1.5">
                {summary.characters.map((char, i) => (
                  <span
                    key={i}
                    className="px-2.5 py-1 rounded-full glass-soft text-[10px] text-slate-600 dark:text-white/70"
                  >
                    {char}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex items-center justify-between mt-5 pt-4 border-t border-black/5 dark:border-white/5">
            {/* Feedback */}
            <div className="flex items-center gap-2">
              <span className="text-[10px] text-slate-400 dark:text-white/40">도움이 됐나요?</span>
              <button
                onClick={() => handleFeedback(true)}
                disabled={localHelpful !== null || feedbackSending}
                className={`p-1.5 rounded-lg transition ${
                  localHelpful === true
                    ? 'text-emerald-500 bg-emerald-500/10'
                    : 'text-slate-400 hover:text-emerald-500 disabled:opacity-50'
                }`}
                title="도움이 됐어요"
              >
                <ThumbsUp className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => handleFeedback(false)}
                disabled={localHelpful !== null || feedbackSending}
                className={`p-1.5 rounded-lg transition ${
                  localHelpful === false
                    ? 'text-red-500 bg-red-500/10'
                    : 'text-slate-400 hover:text-red-500 disabled:opacity-50'
                }`}
                title="별로예요"
              >
                <ThumbsDown className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* Regenerate */}
            <button
              onClick={() => onRegenerate(summary.summaryId)}
              disabled={regenerating}
              className="flex items-center gap-1.5 text-[10px] text-slate-500 dark:text-white/50 hover:text-purple-600 dark:hover:text-purple-400 transition disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${regenerating ? 'animate-spin' : ''}`} />
              재생성
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// ─── Main Page ───────────────────────────────────────────────────────────────

const SummaryPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { user, logout, isMockMode } = useAuth();
  const navigate = useNavigate();

  // Book info
  const [book, setBook] = useState<Book | null>(null);
  const [bookLoading, setBookLoading] = useState(true);

  // Create options
  const [selectedChapter, setSelectedChapter] = useState<number | null>(null);
  const [level, setLevel] = useState<SummaryLevel>('STANDARD');
  const [length, setLength] = useState<SummaryLength>('MEDIUM');

  // Job polling
  const [activeJob, setActiveJob] = useState<SummaryJob | null>(null);
  const [creating, setCreating] = useState(false);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Summaries list
  const [summaries, setSummaries] = useState<Summary[]>([]);
  const [listLoading, setListLoading] = useState(true);

  // Regeneration tracking
  const [regenJobId, setRegenJobId] = useState<number | null>(null);

  // ── Fetch book info ──────────────────────────────────────────────────────
  useEffect(() => {
    if (!bookId) return;
    getBookById(isMockMode, bookId)
      .then((b) => {
        setBook(b);
        if (b.chapters.length > 0) setSelectedChapter(1);
      })
      .catch(() => {})
      .finally(() => setBookLoading(false));
  }, [bookId, isMockMode]);

  // ── Fetch existing summaries ─────────────────────────────────────────────
  const fetchSummaries = useCallback(async () => {
    if (!bookId) return;
    try {
      const { summaries: list } = await getSummaryList(isMockMode, bookId);
      setSummaries(list);
    } catch {
      // 목록 조회 실패는 조용히 처리
    } finally {
      setListLoading(false);
    }
  }, [bookId, isMockMode]);

  useEffect(() => {
    fetchSummaries();
  }, [fetchSummaries]);

  // ── Job polling ──────────────────────────────────────────────────────────
  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  const startPolling = useCallback(
    (jobId: number, onComplete: (summaryId: string) => void) => {
      stopPolling();
      pollingRef.current = setInterval(async () => {
        try {
          const job = await getSummaryJob(isMockMode, jobId);
          setActiveJob(job);
          if (job.status === 'COMPLETED' && job.summaryId) {
            stopPolling();
            onComplete(job.summaryId);
          } else if (job.status === 'FAILED') {
            stopPolling();
            setCreating(false);
            setRegenJobId(null);
          }
        } catch {
          stopPolling();
          setCreating(false);
          setRegenJobId(null);
        }
      }, 2000);
    },
    [isMockMode, stopPolling],
  );

  useEffect(() => () => stopPolling(), [stopPolling]);

  // ── Create summary ───────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!bookId || selectedChapter === null || creating) return;
    setCreating(true);
    setActiveJob(null);
    try {
      const job = await createSummary(isMockMode, bookId, selectedChapter, level, length);
      setActiveJob(job);
      startPolling(job.jobId, async (summaryId) => {
        try {
          const detail = await getSummaryDetail(isMockMode, summaryId);
          setSummaries((prev) => [detail, ...prev]);
        } catch {
          // 상세 조회 실패 시 목록 새로고침
          await fetchSummaries();
        } finally {
          setCreating(false);
          setActiveJob(null);
        }
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : '요약 생성에 실패했습니다.');
      setCreating(false);
    }
  };

  // ── Regenerate ───────────────────────────────────────────────────────────
  const handleRegenerate = async (summaryId: string) => {
    if (regenJobId !== null) return;
    try {
      const job = await regenerateSummary(isMockMode, summaryId);
      setRegenJobId(job.jobId);
      startPolling(job.jobId, async (newSummaryId) => {
        try {
          const detail = await getSummaryDetail(isMockMode, newSummaryId);
          setSummaries((prev) => [detail, ...prev]);
        } catch {
          await fetchSummaries();
        } finally {
          setRegenJobId(null);
        }
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : '재생성에 실패했습니다.');
    }
  };

  // ── Render ───────────────────────────────────────────────────────────────
  if (bookLoading) {
    return (
      <div className="min-h-screen dk-surface flex items-center justify-center relative">
        <div className="dk-grain" />
        <Loader2 className="w-8 h-8 text-purple-500 animate-spin" />
      </div>
    );
  }

  if (!book) {
    return (
      <div className="min-h-screen dk-surface flex items-center justify-center p-6 relative">
        <div className="dk-grain" />
        <div className="glass p-8 rounded-2xl text-center max-w-sm">
          <AlertCircle className="w-8 h-8 text-red-500 mx-auto mb-3" />
          <p className="text-slate-600 dark:text-white/70 text-sm">도서 정보를 불러올 수 없습니다.</p>
          <button
            onClick={() => navigate(-1)}
            className="mt-5 px-5 py-2 rounded-full bg-slate-900 text-white dark:bg-white dark:text-[#0B0E14] text-xs font-semibold"
          >
            돌아가기
          </button>
        </div>
      </div>
    );
  }

  const isJobRunning =
    activeJob?.status === 'QUEUED' || activeJob?.status === 'PROCESSING';

  return (
    <div className="min-h-screen dk-surface flex flex-col p-6 pt-24 selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain" />

      {/* Background Orbs */}
      <div className="absolute top-[8%] right-[12%] w-[420px] h-[420px] rounded-full bg-blue-600/5 dark:bg-blue-900/10 blur-[140px] pointer-events-none" />
      <div className="absolute bottom-[15%] left-[8%] w-[360px] h-[360px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[130px] pointer-events-none" />

      <DkTopNav
        active="home"
        go={(tab) => {
          if (tab === 'home') navigate('/');
          else if (tab === 'me') navigate('/me');
          else if (tab === 'admin') navigate('/admin');
          else alert('준비 중인 기능입니다!');
        }}
        onLogout={logout}
        nickname={user?.nickname || '민'}
      />

      <div className="w-full max-w-3xl mx-auto z-10 flex-1 flex flex-col gap-6">
        {/* Back + Title */}
        <div>
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-1.5 text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white text-xs font-semibold mb-5 transition"
          >
            <DBack className="w-4 h-4" />
            뒤로 가기
          </button>

          <div className="glass rounded-3xl p-6 flex items-center gap-4">
            <div className="w-10 h-10 rounded-2xl bg-blue-500/10 flex items-center justify-center flex-shrink-0">
              <BookOpen className="w-5 h-5 text-blue-500" />
            </div>
            <div>
              <h1 className="font-serif text-lg font-bold text-slate-800 dark:text-white leading-tight">
                {book.title}
              </h1>
              <p className="text-xs text-slate-400 dark:text-white/40 mt-0.5">AI 요약본</p>
            </div>
          </div>
        </div>

        {/* Create Panel */}
        <div className="glass rounded-3xl p-6">
          <h2 className="font-semibold text-sm text-slate-800 dark:text-white flex items-center gap-2 mb-5">
            <Sparkles className="w-4 h-4 text-purple-500" />
            새 요약 생성
          </h2>

          <div className="space-y-4">
            {/* Chapter Selector */}
            <div>
              <p className="text-[10px] font-mono text-slate-400 dark:text-white/40 uppercase tracking-wider mb-2">
                챕터 선택
              </p>
              <div className="grid grid-cols-4 sm:grid-cols-6 gap-1.5">
                {book.chapters.map((_, idx) => {
                  const chapterNum = idx + 1;
                  const isSelected = selectedChapter === chapterNum;
                  return (
                    <button
                      key={chapterNum}
                      onClick={() => setSelectedChapter(chapterNum)}
                      className={`py-2 rounded-xl text-xs font-semibold transition-all border ${
                        isSelected
                          ? 'border-purple-500/60 bg-purple-500/10 text-purple-600 dark:text-purple-300'
                          : 'border-black/5 dark:border-white/5 glass-soft text-slate-600 dark:text-white/60 hover:border-purple-400/30'
                      }`}
                    >
                      {chapterNum}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Level & Length */}
            <OptionPicker
              label="요약 수준"
              options={LEVEL_META}
              selected={level}
              onChange={setLevel}
            />
            <OptionPicker
              label="요약 길이"
              options={LENGTH_META}
              selected={length}
              onChange={setLength}
            />

            {/* Job Status */}
            {activeJob && (
              <div className="flex items-center gap-3 p-3 rounded-xl glass-soft">
                <JobStatusBadge status={activeJob.status} />
                {activeJob.status === 'FAILED' && (
                  <p className="text-xs text-red-500">
                    {activeJob.errorMessage ?? '생성에 실패했습니다. 다시 시도해 주세요.'}
                  </p>
                )}
              </div>
            )}

            {/* Create Button */}
            <button
              onClick={handleCreate}
              disabled={creating || selectedChapter === null}
              className="w-full py-3 rounded-2xl bg-purple-600 hover:bg-purple-700 disabled:opacity-50 text-white text-sm font-semibold transition flex items-center justify-center gap-2"
            >
              {isJobRunning ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  AI가 요약을 생성하고 있습니다...
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  {selectedChapter !== null
                    ? `Chapter ${selectedChapter} 요약 생성`
                    : '챕터를 선택해 주세요'}
                </>
              )}
            </button>
          </div>
        </div>

        {/* Summaries List */}
        <div>
          <h2 className="font-semibold text-sm text-slate-800 dark:text-white flex items-center gap-2 mb-3">
            <BookOpen className="w-4 h-4 text-blue-500" />
            저장된 요약
            {summaries.length > 0 && (
              <span className="ml-1 px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-500 text-[10px] font-mono">
                {summaries.length}
              </span>
            )}
          </h2>

          {listLoading ? (
            <div className="glass rounded-2xl p-8 flex items-center justify-center">
              <Loader2 className="w-6 h-6 text-purple-500 animate-spin" />
            </div>
          ) : summaries.length === 0 ? (
            <div className="glass rounded-2xl p-8 text-center">
              <BookOpen className="w-8 h-8 text-slate-300 dark:text-white/20 mx-auto mb-3" />
              <p className="text-sm text-slate-500 dark:text-white/40">
                아직 생성된 요약이 없습니다.
              </p>
              <p className="text-xs text-slate-400 dark:text-white/30 mt-1">
                챕터를 선택하고 AI 요약을 생성해 보세요.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {summaries.map((s) => (
                <SummaryCard
                  key={s.summaryId}
                  summary={s}
                  isMockMode={isMockMode}
                  onRegenerate={handleRegenerate}
                  regenerating={regenJobId !== null}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SummaryPage;
