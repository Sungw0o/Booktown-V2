import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../api/client';
import {
  registerBook,
  uploadContent,
  getContentJob,
  type RegisterBookRequest,
  type ContentJob,
  type ContentJobStatus,
} from '../api/adminApi';
import {
  Activity,
  Server,
  Database,
  RefreshCw,
  CheckCircle,
  XCircle,
  ExternalLink,
  BarChart3,
  ShieldCheck,
  BookOpen,
  Upload,
  FileText,
  ChevronDown,
  ChevronUp,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { DkTopNav } from '../components/Primitives';

// ─── Health Check Types ───────────────────────────────────────────────────────

interface ServiceStatus {
  status: string;
  services?: {
    mysql: boolean | string;
    redis: boolean | string;
    mongodb: boolean | string;
    chroma: boolean | string;
  };
}

interface ShortcutItem {
  title: string;
  desc: string;
  url: string;
  badgeBg: string;
  badgeText: string;
  hoverBg: string;
  hoverText: string;
  favicon: string;
  fallbackIcon: React.ReactNode;
}

const servicesConfig = [
  { key: 'mysql' as const, name: 'MySQL 데이터베이스', desc: '도서 메타데이터 및 유저 회원 정보 저장소', iconColor: 'text-blue-500 dark:text-blue-400' },
  { key: 'mongodb' as const, name: 'MongoDB', desc: '도서 요약 및 씬(Scene) 정보 문서 저장소', iconColor: 'text-emerald-600 dark:text-emerald-400' },
  { key: 'redis' as const, name: 'Redis', desc: '인메모리 캐싱 및 리프레시 토큰 세션 관리', iconColor: 'text-rose-500' },
  { key: 'chroma' as const, name: 'ChromaDB', desc: '임베딩 벡터 스토어 및 시맨틱 의미 검색 지원', iconColor: 'text-indigo-500 dark:text-indigo-400' },
];

// ─── Shortcut Card ────────────────────────────────────────────────────────────

const ShortcutCard: React.FC<{ item: ShortcutItem }> = ({ item }) => {
  const [imgFailed, setImgFailed] = useState(false);
  return (
    <a href={item.url} target="_blank" rel="noopener noreferrer"
      className="glass rounded-2xl p-6 hover:scale-[1.02] active:scale-[0.99] transition-all duration-300 shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10 flex flex-col group relative overflow-hidden">
      <div className={`absolute top-0 right-0 w-[120px] h-[120px] rounded-bl-full ${item.hoverBg} group-hover:scale-110 transition duration-500`} />
      <div className="flex items-center justify-between mb-4">
        <div className={`w-10 h-10 rounded-xl ${item.badgeBg} flex items-center justify-center ${item.badgeText} overflow-hidden p-1.5`}>
          {!imgFailed && item.favicon
            ? <img src={item.favicon} alt={`${item.title} logo`} className="w-full h-full object-contain" onError={() => setImgFailed(true)} />
            : item.fallbackIcon}
        </div>
        <ExternalLink className={`w-4 h-4 text-slate-400 dark:text-white/35 ${item.hoverText} transition`} />
      </div>
      <h3 className={`font-serif text-[17px] font-medium text-slate-800 dark:text-white ${item.hoverText} transition`}>{item.title}</h3>
      <p className="text-[12px] text-slate-500 dark:text-white/40 font-light mt-1.5 leading-relaxed">{item.desc}</p>
    </a>
  );
};

// ─── Job Status Badge ─────────────────────────────────────────────────────────

const JOB_STATUS_STYLE: Record<ContentJobStatus, string> = {
  QUEUED:     'bg-slate-500/10 text-slate-600 dark:text-slate-300',
  PROCESSING: 'bg-blue-500/10 text-blue-600 dark:text-blue-400',
  COMPLETED:  'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  FAILED:     'bg-red-500/10 text-red-500 dark:text-red-400',
};
const JOB_STATUS_LABEL: Record<ContentJobStatus, string> = {
  QUEUED: '대기 중', PROCESSING: '처리 중', COMPLETED: '완료', FAILED: '실패',
};

const JobStatusBadge: React.FC<{ status: ContentJobStatus }> = ({ status }) => (
  <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${JOB_STATUS_STYLE[status]}`}>
    {status === 'PROCESSING' && <Loader2 className="w-3 h-3 animate-spin" />}
    {status === 'COMPLETED'  && <CheckCircle className="w-3 h-3" />}
    {status === 'FAILED'     && <XCircle className="w-3 h-3" />}
    {JOB_STATUS_LABEL[status]}
  </span>
);

// ─── Book Register Panel ──────────────────────────────────────────────────────

type RegisterStep = 'meta' | 'upload' | 'polling' | 'done';

const GENRE_OPTIONS   = ['소설', '시', '에세이', '역사', '철학', '고전', '기타'];
const COUNTRY_OPTIONS = ['한국', '영국', '프랑스', '러시아', '미국', '독일', '일본', '기타'];
const EMPTY_FORM: RegisterBookRequest = { title: '', author: '', description: '', coverImageUrl: '', genre: '', country: '' };

const BookRegisterPanel: React.FC<{ isMockMode: boolean }> = ({ isMockMode }) => {
  const [expanded, setExpanded]     = useState(false);
  const [step, setStep]             = useState<RegisterStep>('meta');
  const [form, setForm]             = useState<RegisterBookRequest>(EMPTY_FORM);
  const [metaLoading, setMetaLoading] = useState(false);
  const [metaError, setMetaError]   = useState<string | null>(null);
  const [bookId, setBookId]         = useState<number | null>(null);
  const [file, setFile]             = useState<File | null>(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [job, setJob]               = useState<ContentJob | null>(null);
  const fileInputRef                = useRef<HTMLInputElement>(null);
  const pollRef                     = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
  }, []);

  useEffect(() => () => stopPolling(), [stopPolling]);

  const startPolling = useCallback((jobId: number) => {
    stopPolling();
    pollRef.current = setInterval(async () => {
      try {
        const updated = await getContentJob(isMockMode, jobId);
        setJob(updated);
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          stopPolling();
          setStep('done');
        }
      } catch { stopPolling(); }
    }, 3000);
  }, [isMockMode, stopPolling]);

  const handleMetaSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMetaError(null);
    if (!form.title.trim() || !form.author.trim() || !form.genre || !form.country) {
      setMetaError('제목, 저자, 장르, 국가는 필수입니다.');
      return;
    }
    setMetaLoading(true);
    try {
      const res = await registerBook(isMockMode, form);
      setBookId(res.bookId);
      setStep('upload');
    } catch (err) {
      setMetaError(err instanceof Error ? err.message : '도서 등록에 실패했습니다.');
    } finally { setMetaLoading(false); }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    if (f && !f.name.endsWith('.txt')) { setUploadError('TXT 파일만 업로드 가능합니다.'); setFile(null); return; }
    setUploadError(null);
    setFile(f);
  };

  const handleUpload = async () => {
    if (!file || bookId === null) return;
    setUploadError(null);
    setUploadLoading(true);
    try {
      const contentJob = await uploadContent(isMockMode, bookId, file);
      setJob(contentJob);
      setStep('polling');
      startPolling(contentJob.jobId);
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : '업로드에 실패했습니다.');
    } finally { setUploadLoading(false); }
  };

  const handleReset = () => {
    stopPolling();
    setStep('meta'); setForm(EMPTY_FORM); setFile(null);
    setBookId(null); setJob(null); setMetaError(null); setUploadError(null);
  };

  const inputClass = 'w-full rounded-xl glass-soft border border-black/5 dark:border-white/10 px-3.5 py-2.5 text-sm text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-white/30 focus:outline-none focus:ring-2 focus:ring-purple-500/40 transition bg-transparent';
  const labelClass = 'block text-xs font-semibold text-slate-500 dark:text-white/50 mb-1.5';

  const STEP_LABELS = ['① 메타데이터', '② 원문 업로드', '③ 처리 현황'];
  const STEP_KEYS: RegisterStep[] = ['meta', 'upload', 'polling'];

  return (
    <div className="glass rounded-2xl overflow-hidden shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10">
      <button onClick={() => setExpanded(v => !v)}
        className="w-full flex items-center justify-between p-6 text-left hover:bg-black/[0.02] dark:hover:bg-white/[0.03] transition">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-purple-500/10 flex items-center justify-center">
            <BookOpen className="w-4 h-4 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <h2 className="font-semibold text-slate-800 dark:text-white text-sm">도서 등록</h2>
            <p className="text-xs text-slate-500 dark:text-white/40 mt-0.5">메타데이터 입력 → 원문 TXT 업로드 → 파싱·임베딩 Job 처리</p>
          </div>
        </div>
        {expanded ? <ChevronUp className="w-4 h-4 text-slate-400 dark:text-white/30 shrink-0" /> : <ChevronDown className="w-4 h-4 text-slate-400 dark:text-white/30 shrink-0" />}
      </button>

      {expanded && (
        <div className="px-6 pb-6 border-t border-black/5 dark:border-white/5">
          {/* Step indicator */}
          <div className="flex items-center gap-2 mt-5 mb-6">
            {STEP_KEYS.map((s, i) => {
              const idx = STEP_KEYS.indexOf(step === 'done' ? 'polling' : step);
              const isDone = i < idx || (step === 'done' && i <= 2);
              const isActive = i === idx;
              return (
                <React.Fragment key={s}>
                  <span className={`text-xs font-semibold px-2.5 py-1 rounded-full transition ${
                    isDone ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                    : isActive ? 'bg-purple-500/10 text-purple-600 dark:text-purple-400'
                    : 'text-slate-400 dark:text-white/25'}`}>
                    {STEP_LABELS[i]}
                  </span>
                  {i < 2 && <span className="text-slate-300 dark:text-white/20 text-xs">→</span>}
                </React.Fragment>
              );
            })}
          </div>

          {/* Step 1: Metadata */}
          {step === 'meta' && (
            <form onSubmit={handleMetaSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>제목 *</label>
                  <input className={inputClass} placeholder="예) 토지" value={form.title}
                    onChange={e => setForm(f => ({ ...f, title: e.target.value }))} />
                </div>
                <div>
                  <label className={labelClass}>저자 *</label>
                  <input className={inputClass} placeholder="예) 박경리" value={form.author}
                    onChange={e => setForm(f => ({ ...f, author: e.target.value }))} />
                </div>
                <div>
                  <label className={labelClass}>장르 *</label>
                  <select className={inputClass} value={form.genre} onChange={e => setForm(f => ({ ...f, genre: e.target.value }))}>
                    <option value="">장르 선택</option>
                    {GENRE_OPTIONS.map(g => <option key={g} value={g}>{g}</option>)}
                  </select>
                </div>
                <div>
                  <label className={labelClass}>국가 *</label>
                  <select className={inputClass} value={form.country} onChange={e => setForm(f => ({ ...f, country: e.target.value }))}>
                    <option value="">국가 선택</option>
                    {COUNTRY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="md:col-span-2">
                  <label className={labelClass}>설명</label>
                  <textarea className={`${inputClass} resize-none`} rows={3} placeholder="도서 소개 (선택)"
                    value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
                </div>
                <div className="md:col-span-2">
                  <label className={labelClass}>표지 이미지 URL</label>
                  <input className={inputClass} placeholder="https://... (선택)" value={form.coverImageUrl}
                    onChange={e => setForm(f => ({ ...f, coverImageUrl: e.target.value }))} />
                </div>
              </div>
              {metaError && (
                <div className="flex items-center gap-2 text-red-500 text-xs bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-3">
                  <AlertCircle className="w-4 h-4 shrink-0" />{metaError}
                </div>
              )}
              <div className="flex justify-end pt-2">
                <button type="submit" disabled={metaLoading}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold disabled:opacity-50 transition">
                  {metaLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                  도서 등록
                </button>
              </div>
            </form>
          )}

          {/* Step 2: File upload */}
          {step === 'upload' && bookId !== null && (
            <div className="space-y-5">
              <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 text-sm font-medium">
                <CheckCircle className="w-4 h-4" />
                도서 등록 완료 — Book ID: <span className="font-mono font-bold">{bookId}</span>
              </div>
              <div onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-purple-500/30 hover:border-purple-500/60 rounded-2xl p-10 flex flex-col items-center gap-3 cursor-pointer transition group">
                <Upload className="w-8 h-8 text-purple-500/50 group-hover:text-purple-500 transition" />
                <p className="text-sm font-medium text-slate-600 dark:text-white/60 group-hover:text-slate-800 dark:group-hover:text-white transition">
                  {file ? file.name : 'TXT 원문 파일 클릭하여 선택'}
                </p>
                {file && <span className="text-xs text-slate-400 dark:text-white/35">{(file.size / 1024).toFixed(1)} KB</span>}
                <span className="text-[11px] text-slate-400 dark:text-white/25">.txt 파일만 허용</span>
              </div>
              <input ref={fileInputRef} type="file" accept=".txt" className="hidden" onChange={handleFileChange} />
              {uploadError && (
                <div className="flex items-center gap-2 text-red-500 text-xs bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-3">
                  <AlertCircle className="w-4 h-4 shrink-0" />{uploadError}
                </div>
              )}
              <div className="flex justify-between pt-1">
                <button onClick={handleReset} className="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-white/60 transition">처음으로</button>
                <button onClick={handleUpload} disabled={!file || uploadLoading}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold disabled:opacity-50 transition">
                  {uploadLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                  원문 업로드
                </button>
              </div>
            </div>
          )}

          {/* Step 3: Job polling / done */}
          {(step === 'polling' || step === 'done') && job && (
            <div className="space-y-4">
              <div className="glass-soft rounded-2xl p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500 dark:text-white/40 uppercase tracking-wider">콘텐츠 Job 상태</span>
                  <JobStatusBadge status={job.status} />
                </div>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <span className="text-slate-400 dark:text-white/30">Job ID</span>
                    <p className="font-mono font-bold text-slate-700 dark:text-white mt-0.5">{job.jobId}</p>
                  </div>
                  <div>
                    <span className="text-slate-400 dark:text-white/30">Book ID</span>
                    <p className="font-mono font-bold text-slate-700 dark:text-white mt-0.5">{job.bookId || bookId}</p>
                  </div>
                  {job.chapterCount !== null && (
                    <div>
                      <span className="text-slate-400 dark:text-white/30">챕터 수</span>
                      <p className="font-bold text-slate-700 dark:text-white mt-0.5">{job.chapterCount}개</p>
                    </div>
                  )}
                  {job.errorMessage && (
                    <div className="col-span-2">
                      <span className="text-red-400">오류</span>
                      <p className="text-red-500 mt-0.5">{job.errorMessage}</p>
                    </div>
                  )}
                </div>
                <div className="w-full h-1.5 bg-black/5 dark:bg-white/5 rounded-full overflow-hidden">
                  <div className={`h-full rounded-full transition-all duration-700 ${
                    job.status === 'COMPLETED' ? 'w-full bg-emerald-500'
                    : job.status === 'FAILED' ? 'w-full bg-red-500'
                    : job.status === 'PROCESSING' ? 'w-2/3 bg-blue-500 animate-pulse'
                    : 'w-1/4 bg-slate-400'}`} />
                </div>
                {step === 'polling' && <p className="text-[11px] text-slate-400 dark:text-white/30 animate-pulse">3초마다 자동 갱신 중...</p>}
              </div>
              {step === 'done' && (
                <div className="flex justify-between items-center pt-1">
                  {job.status === 'COMPLETED'
                    ? <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 text-sm font-medium"><CheckCircle className="w-4 h-4" />파싱 완료! 도서가 서비스에 반영됩니다.</div>
                    : <div className="flex items-center gap-2 text-red-500 text-sm"><XCircle className="w-4 h-4" />처리 실패 {job.retryable && '— 재시도 가능'}</div>}
                  <button onClick={handleReset}
                    className="text-xs px-4 py-2 rounded-xl glass-soft text-slate-600 dark:text-white/60 hover:text-slate-800 dark:hover:text-white transition">
                    새 도서 등록
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─── Admin Dashboard ──────────────────────────────────────────────────────────

const AdminDashboard: React.FC = () => {
  const { user, logout, isMockMode } = useAuth();
  const navigate = useNavigate();
  const [data, setData]             = useState<ServiceStatus | null>(null);
  const [loading, setLoading]       = useState<boolean>(true);
  const [error, setError]           = useState<string | null>(null);
  const [refreshCount, setRefreshCount] = useState<number>(0);

  const grafanaUrl = import.meta.env.VITE_GRAFANA_URL || '/monitoring';
  const sonarUrl   = import.meta.env.VITE_SONARQUBE_URL || 'https://sonarcloud.io/summary/new_code?id=BookTown_BookTown-Frontend-V2';
  const swaggerUrl = import.meta.env.VITE_SWAGGER_URL || 'https://api.booktown.shop/api/v1/swagger-ui/index.html';

  const shortcutConfig: ShortcutItem[] = [
    {
      title: 'Grafana 모니터링',
      desc: '서버 CPU, 메모리, 디스크 및 트래픽을 실시간 대시보드로 시각화합니다.',
      url: grafanaUrl,
      badgeBg: 'bg-purple-500/10', badgeText: 'text-purple-600 dark:text-purple-400',
      hoverBg: 'bg-purple-600/5 dark:bg-purple-600/10', hoverText: 'group-hover:text-purple-500',
      favicon: 'https://raw.githubusercontent.com/grafana/grafana/main/public/img/fav32.png',
      fallbackIcon: <BarChart3 className="w-5 h-5" />,
    },
    {
      title: 'SonarCloud 품질 검사',
      desc: '정적 코드 분석으로 보안 취약점, 버그, 중복률을 측정합니다.',
      url: sonarUrl,
      badgeBg: 'bg-amber-500/10', badgeText: 'text-amber-600 dark:text-amber-400',
      hoverBg: 'bg-amber-500/5 dark:bg-amber-500/10', hoverText: 'group-hover:text-amber-500',
      favicon: 'https://sonarcloud.io/favicon.ico',
      fallbackIcon: <ShieldCheck className="w-5 h-5" />,
    },
    {
      title: 'Swagger API 명세서',
      desc: '백엔드 API 엔드포인트를 브라우저에서 직접 테스트하고 조회합니다.',
      url: swaggerUrl,
      badgeBg: 'bg-blue-500/10', badgeText: 'text-blue-600 dark:text-blue-400',
      hoverBg: 'bg-blue-600/5 dark:bg-blue-600/10', hoverText: 'group-hover:text-blue-500',
      favicon: 'https://swagger.io/favicon.ico',
      fallbackIcon: <BookOpen className="w-5 h-5" />,
    },
  ];

  const fetchHealth = async () => {
    setLoading(true); setError(null);
    try {
      const response = await client.get('/health');
      setData(response.data.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch backend health status');
    } finally { setLoading(false); }
  };

  useEffect(() => {
    const t = setTimeout(() => fetchHealth(), 0);
    const i = setInterval(() => fetchHealth(), 30000);
    return () => { clearTimeout(t); clearInterval(i); };
  }, [refreshCount]);

  return (
    <div className="min-h-screen dk-surface flex flex-col p-6 pt-24 selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain" />
      <DkTopNav
        active="admin"
        go={tab => {
          if (tab === 'home') navigate('/');
          else if (tab === 'admin') navigate('/admin');
          else alert('준비 중인 기능입니다!');
        }}
        onLogout={logout}
        nickname={user?.nickname || '민'}
      />

      <div className="absolute top-[10%] left-[20%] w-[400px] h-[400px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[130px] pointer-events-none animate-pulse" />
      <div className="absolute bottom-[20%] right-[10%] w-[350px] h-[350px] rounded-full bg-amber-600/5 dark:bg-amber-900/10 blur-[120px] pointer-events-none animate-pulse delay-1000" />

      <div className="w-full max-w-6xl mx-auto z-10 flex-1 flex flex-col">
        {/* Header */}
        <div className="mb-10 text-left">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-purple-500/20 bg-purple-500/5 text-purple-600 dark:text-purple-400 text-xs font-semibold uppercase tracking-wider mb-3 font-mono">
            <Activity className="w-3.5 h-3.5 animate-pulse" />System Administration
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-slate-900 via-slate-800 to-purple-800 dark:from-white dark:via-slate-200 dark:to-purple-400 font-display">
            책고을 시스템 관리자
          </h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1.5 font-light">
            서버 인프라의 실시간 헬스체크 및 개발 모니터링 대시보드 바로가기를 제공합니다. (30초마다 자동 갱신)
          </p>
        </div>

        {/* Health & Shortcuts */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
          <div className="lg:col-span-2 flex flex-col gap-6">
            <div className="glass rounded-2xl p-6 shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10 flex-1 flex flex-col">
              <div className="flex items-center justify-between border-b border-black/5 dark:border-white/5 pb-4 mb-6">
                <div className="flex items-center gap-3">
                  <Server className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                  <span className="font-semibold text-slate-800 dark:text-slate-200">백엔드 API 게이트웨이 상태</span>
                </div>
                <button
                  onClick={() => setRefreshCount(p => p + 1)}
                  disabled={loading}
                  className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 disabled:opacity-50 transition-all duration-200 hover:rotate-180"
                  title="새로고침"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </button>
              </div>
              {loading && !data ? (
                <div className="flex-1 flex flex-col items-center justify-center py-20 gap-3">
                  <RefreshCw className="w-8 h-8 text-purple-500 animate-spin" />
                  <p className="text-slate-500 text-xs animate-pulse">코어 서비스 상태를 조회하고 있습니다...</p>
                </div>
              ) : error ? (
                <div className="bg-red-500/10 border border-red-500/20 rounded-xl p-4 flex items-start gap-3 my-4">
                  <XCircle className="w-5 h-5 text-red-500 dark:text-red-400 shrink-0 mt-0.5" />
                  <div>
                    <h4 className="font-semibold text-red-800 dark:text-red-200 text-sm">서버 연결 실패</h4>
                    <p className="text-red-600/80 dark:text-red-400/80 text-xs mt-1">{error}</p>
                  </div>
                </div>
              ) : data ? (
                <div className="space-y-6 flex-1">
                  <div className="flex items-center justify-between p-4 rounded-xl bg-black/5 dark:bg-white/5 border border-black/5 dark:border-white/5">
                    <div className="flex flex-col">
                      <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">종합 시스템 상태</span>
                      {loading && <span className="text-[10px] text-purple-500 animate-pulse mt-0.5 font-light">업데이트 중...</span>}
                    </div>
                    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold ${
                      data.status === 'UP'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                        : 'bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20'
                    }`}>
                      {data.status === 'UP' ? <CheckCircle className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
                      SYSTEM {data.status}
                    </span>
                  </div>
                  <div className="space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 mb-2">연결된 인프라 리소스</h3>
                    {servicesConfig.map(srv => {
                      const srvVal = data.services?.[srv.key];
                      const isConnected = srvVal === true || srvVal === 'UP';
                      return (
                        <div key={srv.key} className="glass-soft hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition-colors rounded-xl p-3.5 flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <Database className={`w-5 h-5 ${srv.iconColor}`} />
                            <div>
                              <span className="text-sm font-semibold text-slate-700 dark:text-slate-300 block">{srv.name}</span>
                              <span className="text-[10px] text-slate-500 dark:text-slate-400/70">{srv.desc}</span>
                            </div>
                          </div>
                          <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${
                            isConnected ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-red-500/10 text-red-600 dark:text-red-400'
                          }`}>
                            {isConnected ? 'Connected' : 'Disconnected'}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : <p className="text-center text-slate-500 py-6">정보를 불러올 수 없습니다.</p>}
            </div>
          </div>

          <div className="flex flex-col gap-6">
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 px-1">외부 모니터링 및 품질 대시보드</h2>
            {shortcutConfig.map((item, idx) => <ShortcutCard key={idx} item={item} />)}
          </div>
        </div>

        {/* Book Register Panel */}
        <div className="mb-12">
          <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 px-1 mb-4 flex items-center gap-1.5">
            <FileText className="w-3.5 h-3.5" />콘텐츠 관리
          </h2>
          <BookRegisterPanel isMockMode={isMockMode} />
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
