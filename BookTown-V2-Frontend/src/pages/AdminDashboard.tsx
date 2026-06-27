import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../api/client';
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
  BookOpen
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { DkTopNav } from '../components/Primitives';

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
  {
    key: 'mysql' as const,
    name: 'MySQL 데이터베이스',
    desc: '도서 메타데이터 및 유저 회원 정보 저장소',
    iconColor: 'text-blue-500 dark:text-blue-400',
  },
  {
    key: 'mongodb' as const,
    name: 'MongoDB',
    desc: '도서 요약 및 씬(Scene) 정보 문서 저장소',
    iconColor: 'text-emerald-600 dark:text-emerald-400',
  },
  {
    key: 'redis' as const,
    name: 'Redis',
    desc: '인메모리 캐싱 및 리프레시 토큰 세션 관리',
    iconColor: 'text-rose-500',
  },
  {
    key: 'chroma' as const,
    name: 'ChromaDB',
    desc: '임베딩 벡터 스토어 및 시맨틱 의미 검색 지원',
    iconColor: 'text-indigo-500 dark:text-indigo-400',
  },
];

const ShortcutCard: React.FC<{ item: ShortcutItem }> = ({ item }) => {
  const [imgFailed, setImgFailed] = useState(false);

  return (
    <a 
      href={item.url} 
      target="_blank" 
      rel="noopener noreferrer"
      className="glass rounded-2xl p-6 hover:scale-[1.02] active:scale-[0.99] transition-all duration-300 shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10 flex flex-col group relative overflow-hidden"
    >
      <div className={`absolute top-0 right-0 w-[120px] h-[120px] rounded-bl-full ${item.hoverBg} group-hover:scale-110 transition duration-500`} />
      <div className="flex items-center justify-between mb-4">
        <div className={`w-10 h-10 rounded-xl ${item.badgeBg} flex items-center justify-center ${item.badgeText} overflow-hidden p-1.5`}>
          {!imgFailed && item.favicon ? (
            <img 
              src={item.favicon} 
              alt={`${item.title} logo`} 
              className="w-full h-full object-contain"
              onError={() => setImgFailed(true)} 
            />
          ) : (
            item.fallbackIcon
          )}
        </div>
        <ExternalLink className={`w-4 h-4 text-slate-400 dark:text-white/35 ${item.hoverText} transition`} />
      </div>
      <h3 className={`font-serif text-[17px] font-medium text-slate-800 dark:text-white ${item.hoverText} transition`}>
        {item.title}
      </h3>
      <p className="text-[12px] text-slate-500 dark:text-white/40 font-light mt-1.5 leading-relaxed">
        {item.desc}
      </p>
    </a>
  );
};

const AdminDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<ServiceStatus | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshCount, setRefreshCount] = useState<number>(0);

  // 대시보드 URL 설정 (환경변수 또는 합리적 기본값)
  const grafanaUrl = import.meta.env.VITE_GRAFANA_URL || '/monitoring';
  const sonarUrl = import.meta.env.VITE_SONARQUBE_URL || 'https://sonarcloud.io/summary/new_code?id=BookTown_BookTown-Frontend-V2';
  const swaggerUrl = import.meta.env.VITE_SWAGGER_URL || 'https://api.booktown.shop/api/v1/swagger-ui/index.html';

  const shortcutConfig: ShortcutItem[] = [
    {
      title: 'Grafana 모니터링',
      desc: '서버의 CPU, 메모리, 디스크 자원 사용량과 트래픽 동향을 실시간 대시보드로 시각화하여 관측합니다.',
      url: grafanaUrl,
      badgeBg: 'bg-purple-500/10',
      badgeText: 'text-purple-600 dark:text-purple-400',
      hoverBg: 'bg-purple-600/5 dark:bg-purple-600/10',
      hoverText: 'group-hover:text-purple-500',
      favicon: 'https://raw.githubusercontent.com/grafana/grafana/main/public/img/fav32.png',
      fallbackIcon: <BarChart3 className="w-5 h-5" />,
    },
    {
      title: 'SonarCloud 품질 검사',
      desc: '정적 코드 분석을 통해 보안 취약점, 버그 가능성, 중복률 및 코드 스멜을 측정하고 정량적인 메트릭을 추적합니다.',
      url: sonarUrl,
      badgeBg: 'bg-amber-500/10',
      badgeText: 'text-amber-600 dark:text-amber-400',
      hoverBg: 'bg-amber-500/5 dark:bg-amber-500/10',
      hoverText: 'group-hover:text-amber-500',
      favicon: 'https://sonarcloud.io/favicon.ico',
      fallbackIcon: <ShieldCheck className="w-5 h-5" />,
    },
    {
      title: 'Swagger API 명세서',
      desc: '백엔드 API 엔드포인트 설계서 및 명세 문서를 브라우저에서 직접 테스트하고 실시간으로 조회합니다.',
      url: swaggerUrl,
      badgeBg: 'bg-blue-500/10',
      badgeText: 'text-blue-600 dark:text-blue-400',
      hoverBg: 'bg-blue-600/5 dark:bg-blue-600/10',
      hoverText: 'group-hover:text-blue-500',
      favicon: 'https://swagger.io/favicon.ico',
      fallbackIcon: <BookOpen className="w-5 h-5" />,
    },
  ];

  const fetchHealth = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await client.get('/health');
      setData(response.data.data);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(message || 'Failed to fetch backend health status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 즉시 호출
    const timerId = setTimeout(() => {
      fetchHealth();
    }, 0);

    // 30초마다 자동 리프레시 진행
    const intervalId = setInterval(() => {
      fetchHealth();
    }, 30000);

    return () => {
      clearTimeout(timerId);
      clearInterval(intervalId);
    };
  }, [refreshCount]);

  const handleRefresh = () => {
    setRefreshCount((prev) => prev + 1);
  };

  const handleGo = (tab: string) => {
    if (tab === 'home') {
      navigate('/');
    } else if (tab === 'admin') {
      navigate('/admin');
    } else {
      alert('준비 중인 기능입니다!');
    }
  };

  return (
    <div className="min-h-screen dk-surface flex flex-col p-6 pt-24 selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain" />
      <DkTopNav
        active="admin"
        go={handleGo}
        onLogout={logout}
        nickname={user?.nickname || '민'}
      />

      {/* Background Gradient Orbs */}
      <div className="absolute top-[10%] left-[20%] w-[400px] h-[400px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[130px] pointer-events-none animate-pulse" />
      <div className="absolute bottom-[20%] right-[10%] w-[350px] h-[350px] rounded-full bg-amber-600/5 dark:bg-amber-900/10 blur-[120px] pointer-events-none animate-pulse delay-1000" />

      <div className="w-full max-w-6xl mx-auto z-10 flex-1 flex flex-col">
        {/* Header */}
        <div className="mb-10 text-left">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-purple-500/20 bg-purple-500/5 text-purple-600 dark:text-purple-400 text-xs font-semibold uppercase tracking-wider mb-3 font-mono">
            <Activity className="w-3.5 h-3.5 animate-pulse" />
            System Administration
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-slate-900 via-slate-800 to-purple-800 dark:from-white dark:via-slate-200 dark:to-purple-400 font-display">
            책고을 시스템 관리자
          </h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1.5 font-light">
            서버 인프라의 실시간 헬스체크 및 개발 모니터링 대시보드 바로가기를 제공합니다. (30초마다 자동 갱신)
          </p>
        </div>

        {/* Main Grid: Health Check and Dashboard Shortcuts */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-12">
          
          {/* Health Check Section (Left 2/3) */}
          <div className="lg:col-span-2 flex flex-col gap-6">
            <div className="glass rounded-2xl p-6 shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10 flex-1 flex flex-col">
              <div className="flex items-center justify-between border-b border-black/5 dark:border-white/5 pb-4 mb-6">
                <div className="flex items-center gap-3">
                  <Server className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                  <span className="font-semibold text-slate-800 dark:text-slate-200">백엔드 API 게이트웨이 상태</span>
                </div>
                <button
                  onClick={handleRefresh}
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
                <div className="flex-1 flex flex-col justify-center py-8">
                  <div className="bg-red-500/10 border border-red-500/20 rounded-xl p-4 flex items-start gap-3 my-4">
                    <XCircle className="w-5 h-5 text-red-500 dark:text-red-400 shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-semibold text-red-800 dark:text-red-200 text-sm">서버 연결 실패</h4>
                      <p className="text-red-600/80 dark:text-red-400/80 text-xs mt-1">{error}</p>
                    </div>
                  </div>
                </div>
              ) : data ? (
                <div className="space-y-6 flex-1">
                  {/* Overall Status */}
                  <div className="flex items-center justify-between p-4 rounded-xl bg-black/5 dark:bg-white/5 border border-black/5 dark:border-white/5">
                    <div className="flex flex-col">
                      <span className="text-xs text-slate-500 dark:text-slate-400 font-medium font-sans">종합 시스템 상태</span>
                      {loading && (
                        <span className="text-[10px] text-purple-500 animate-pulse mt-0.5 font-light">업데이트 중...</span>
                      )}
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

                  {/* Service Details */}
                  <div className="space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 mb-2">연결된 인프라 리소스</h3>
                    
                    {servicesConfig.map((srv) => {
                      const srvVal = data.services?.[srv.key];
                      // 백엔드가 boolean(true/false)을 주거나, 혹은 문자열 'UP'을 줄 수 있으므로 둘 다 지원
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
              ) : (
                <p className="text-center text-slate-500 py-6">정보를 불러올 수 없습니다.</p>
              )}
            </div>
          </div>

          {/* Monitoring Dashboards Shortcut (Right 1/3) */}
          <div className="flex flex-col gap-6">
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 px-1">
              외부 모니터링 및 품질 대시보드
            </h2>

            {shortcutConfig.map((item, idx) => (
              <ShortcutCard key={idx} item={item} />
            ))}
          </div>

        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
