import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { DkTopNav } from '../components/Primitives';

const ERROR_META = {
  403: {
    code: '403',
    name: 'Forbidden',
    ko: '접근 권한이 없어요',
    desc: '이 페이지에 접근할 수 있는 권한이 없습니다. 관리자에게 문의해 주세요.',
    cta: '홈으로 돌아가기',
    ctaAlt: '문의하기',
    glyph: '🚫',
    color: '#D46A6A',
    bg: 'rgba(212,106,106,0.08)',
  },
  404: {
    code: '404',
    name: 'Not Found',
    ko: '페이지를 찾을 수 없어요',
    desc: '요청하신 페이지 또는 도서가 존재하지 않거나 삭제됐습니다.',
    cta: '탐색으로 가기',
    ctaAlt: '검색',
    glyph: '?',
    color: '#B89A6A',
    bg: 'rgba(184,154,106,0.08)',
  },
};

interface DkErrorScreenProps {
  code: 403 | 404;
}

export const DkErrorScreen: React.FC<DkErrorScreenProps> = ({ code }) => {
  const navigate = useNavigate();
  const { user, logout, isAuthenticated } = useAuth();
  const m = ERROR_META[code];
  const [reqId] = React.useState(() => `${m.code}-${Math.random().toString(36).slice(2, 10).toUpperCase()}`);
  const [timestamp] = React.useState(() => new Date().toISOString().slice(0, 19) + 'Z');

  const handleGo = (tab: string) => {
    if (tab === 'home') {
      navigate('/');
    } else if (tab === 'admin') {
      navigate('/admin');
    } else {
      alert('준비 중인 기능입니다!');
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen dk-surface flex flex-col relative overflow-hidden text-white selection:bg-purple-500 selection:text-white">
      <div className="dk-grain absolute inset-0 opacity-40 pointer-events-none" />
      
      {/* Mobile view */}
      <div className="md:hidden flex-1 relative flex flex-col items-center justify-center px-7 text-center">
        <div className="absolute inset-0 pointer-events-none" style={{
          background: `radial-gradient(60% 50% at 50% 40%, ${m.bg}, transparent 70%)`
        }} />
        
        {/* code glyph */}
        <div className="relative w-24 h-24 rounded-3xl grid place-items-center mb-6" style={{ background: m.bg, border: `1px solid ${m.color}30` }}>
          <div className="text-[40px] leading-none">{m.glyph}</div>
        </div>
        
        <div className="font-mono text-[11px] uppercase tracking-[0.2em] mb-2" style={{ color: m.color }}>{m.code} · {m.name}</div>
        <div className="font-display text-[30px] leading-[1.1] mb-3 font-semibold">{m.ko}</div>
        <p className="text-[13px] text-white/55 leading-[1.7] font-light max-w-[28ch] mb-7">{m.desc}</p>
        
        <div className="flex flex-col gap-2 w-full max-w-[260px] relative z-10">
          <button 
            onClick={() => navigate('/')} 
            className="w-full py-3 rounded-full text-[13px] text-white font-medium transition active:scale-[0.98]"
            style={{ background: `linear-gradient(135deg, ${m.color}cc, ${m.color}88)`, boxShadow: `0 4px 16px ${m.color}30` }}
          >
            {m.cta}
          </button>
          <button 
            onClick={() => navigate('/')} 
            className="w-full py-3 rounded-full text-[12px] glass-soft text-white/65 hover:text-white transition active:scale-[0.98]"
          >
            {m.ctaAlt}
          </button>
        </div>
        
        <div className="absolute bottom-6 left-0 right-0 flex justify-center font-mono text-[9px] text-white/20">
          request_id: {reqId}
        </div>
      </div>

      {/* Web/Desktop view */}
      <div className="hidden md:flex flex-col flex-1 relative">
        <DkTopNav 
          active="home" 
          go={handleGo} 
          onLogout={isAuthenticated ? handleLogout : undefined} 
          nickname={user?.nickname || '민'} 
        />
        
        <div className="absolute inset-0 top-16 grid place-items-center px-10">
          <div className="absolute inset-0 pointer-events-none" style={{
            background: `radial-gradient(70% 60% at 50% 50%, ${m.bg}, transparent 65%)`
          }} />
          
          <div className="relative text-center max-w-[560px]">
            {/* massive code */}
            <div className="font-display leading-none mb-4 font-black select-none" style={{ fontSize: 'clamp(80px,12vw,140px)', color: `${m.color}22`, letterSpacing: '-0.04em' }}>
              {m.code}
            </div>
            
            <div className="relative -mt-12 mb-6">
              <div className="w-20 h-20 rounded-3xl grid place-items-center mx-auto" style={{ background: m.bg, border: `1px solid ${m.color}30` }}>
                <div className="text-[36px]">{m.glyph}</div>
              </div>
            </div>
            
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] mb-3" style={{ color: m.color }}>{m.code} · {m.name}</div>
            <h1 className="font-display text-[42px] leading-[1.05] mb-4 font-bold">{m.ko}</h1>
            <p className="text-[14px] text-white/55 leading-[1.7] font-light mb-8 max-w-[42ch] mx-auto">{m.desc}</p>
            
            <div className="flex items-center justify-center gap-3 relative z-10">
              <button 
                onClick={() => navigate('/')} 
                className="px-7 py-3.5 rounded-full text-[13px] text-white font-medium transition hover:scale-[1.02] active:scale-[0.98]"
                style={{ background: `linear-gradient(135deg, ${m.color}cc, ${m.color}88)`, boxShadow: `0 4px 20px ${m.color}35` }}
              >
                {m.cta}
              </button>
              <button 
                onClick={() => navigate('/')} 
                className="px-6 py-3.5 rounded-full text-[13px] glass-soft text-white/70 hover:text-white transition active:scale-[0.98]"
              >
                {m.ctaAlt}
              </button>
            </div>
            
            <div className="mt-8 font-mono text-[10px] text-white/25">
              request_id: {reqId} · {timestamp}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export const NotFoundPage: React.FC = () => {
  return <DkErrorScreen code={404} />;
};

export const ForbiddenPage: React.FC = () => {
  return <DkErrorScreen code={403} />;
};
