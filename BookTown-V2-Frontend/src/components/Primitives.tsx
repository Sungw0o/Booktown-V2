import React, { useState, useEffect } from 'react';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

/* ---------- Icons (monoline, currentColor) ---------- */
export const DSearch: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" {...p}>
    <circle cx="11" cy="11" r="7"/>
    <path d="m20 20-3.5-3.5"/>
  </svg>
);

export const DBack: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="m14 6-6 6 6 6"/>
  </svg>
);

export const DNext: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="m10 6 6 6-6 6"/>
  </svg>
);

export const DArrow: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M5 12h14M13 6l6 6-6 6"/>
  </svg>
);

export const DBell: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8M10 21a2 2 0 0 0 4 0"/>
  </svg>
);

export const DSpark: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/>
  </svg>
);

export const DImage: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <rect x="3" y="4" width="18" height="16" rx="2"/>
    <circle cx="9" cy="10" r="1.5"/>
    <path d="m4 19 5-5 4 4 3-3 4 4"/>
  </svg>
);

export const DHelp: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <circle cx="12" cy="12" r="9"/>
    <path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.8.4-1 1-1 1.7M12 17h.01"/>
  </svg>
);

export const DUser: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <circle cx="12" cy="8" r="4"/>
    <path d="M4 21a8 8 0 0 1 16 0"/>
  </svg>
);

export const DCompass: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <circle cx="12" cy="12" r="9"/>
    <path d="m15 9-2 5-5 2 2-5 5-2z"/>
  </svg>
);

export const DClock: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <circle cx="12" cy="12" r="9"/>
    <path d="M12 7v5l3 2"/>
  </svg>
);

export const DBookmark: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M6 3h12v18l-6-4-6 4z"/>
  </svg>
);

export const DCheck: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M5 12.5 10 17 19 7.5"/>
  </svg>
);

export const DX: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M6 6l12 12M18 6 6 18"/>
  </svg>
);

export const DPlus: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" {...p}>
    <path d="M12 5v14M5 12h14"/>
  </svg>
);

export const DRefresh: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M3 12a9 9 0 0 1 15.5-6.2L21 8M21 4v4h-4M21 12a9 9 0 0 1-15.5 6.2L3 16M3 20v-4h4"/>
  </svg>
);

export const DLayers: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="m12 3 9 5-9 5-9-5z"/>
    <path d="m3 13 9 5 9-5M3 18l9 5 9-5"/>
  </svg>
);

export const DThumbsUp: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <path d="M7 10v10H4V10zM7 10l4-7a2 2 0 0 1 3.6 1.4L14 9h5a2 2 0 0 1 2 2.3l-1 7A2 2 0 0 1 18 20H7"/>
  </svg>
);

export const DSettings: React.FC<React.SVGProps<SVGSVGElement>> = (p) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}>
    <circle cx="12" cy="12" r="3"/>
    <circle cx="12" cy="12" r="9"/>
  </svg>
);

/* ---------- Book interface ---------- */
export interface BookInfo {
  id: string;
  title: string;
  author: string;
  en?: string;
}

interface Palette {
  bg: string;
  g1: string;
  g2: string;
  acc: string;
}

const palettes: Record<string, Palette> = {
  pride:      { bg: '#2A1F1B', g1: '#8B4A3F', g2: '#3E2520', acc: '#E8B86F' },
  gatsby:     { bg: '#0F1F2E', g1: '#1D4E6B', g2: '#0A1622', acc: '#E9C46A' },
  romeo:      { bg: '#1F0E12', g1: '#7A1F2B', g2: '#0F0608', acc: '#D4A25A' },
  miserables: { bg: '#0F141C', g1: '#2E3A4B', g2: '#080B12', acc: '#B89A6A' },
  crime:      { bg: '#1A140E', g1: '#4A3F35', g2: '#0E0A06', acc: '#C9B89A' },
  jane:       { bg: '#0F1A14', g1: '#2F4A3A', g2: '#070D0A', acc: '#D3B77A' },
};

/* ---------- Cinematic book cover ---------- */
export interface DkCoverProps {
  book: BookInfo;
  className?: string;
}

export const DkCover: React.FC<DkCoverProps> = ({ book, className = '' }) => {
  const p = palettes[book.id] || palettes.pride;
  return (
    <div
      className={`cover ${className}`}
      style={{
        background: `
          radial-gradient(120% 80% at 30% 20%, ${p.g1}88 0%, transparent 55%),
          radial-gradient(100% 60% at 80% 90%, ${p.g2} 0%, transparent 60%),
          linear-gradient(160deg, ${p.bg} 0%, ${p.g2} 100%)
        `,
      }}
    >
      <div className="sheen" />
      <div className="grain" />
      <div className="absolute inset-0 p-3 flex flex-col justify-end">
        <div
          className="font-serif text-white text-[12px] leading-[1.15] font-medium"
          style={{ textShadow: '0 1px 4px rgba(0,0,0,0.6)' }}
        >
          {book.title}
        </div>
        <div className="text-[9px] text-white/55 mt-0.5 font-mono uppercase tracking-[0.12em]">{book.author}</div>
      </div>
      <div className="absolute top-3 right-3 w-1.5 h-1.5 rounded-full" style={{ background: p.acc, boxShadow: `0 0 8px ${p.acc}` }} />
    </div>
  );
};

/* ---------- Cinematic illustration field ---------- */
export interface DkIllustProps {
  tones?: [string, string, string];
  className?: string;
  busy?: boolean;
  opacity?: number;
}

export const DkIllust: React.FC<DkIllustProps> = ({
  tones = ['#7AA3D6', '#1D4E6B', '#0A1622'],
  className = '',
  busy = false,
  opacity = 1,
}) => {
  const [a, b, c] = tones;
  return (
    <div
      className={`relative overflow-hidden rounded-md ${className}`}
      style={{
        background: `
          radial-gradient(60% 55% at 25% 30%, ${a}aa, transparent 65%),
          radial-gradient(55% 50% at 75% 70%, ${b}cc, transparent 60%),
          linear-gradient(135deg, ${c} 0%, #050810 100%)
        `,
        filter: busy ? 'blur(8px) saturate(1.1)' : 'blur(2px) saturate(1.1)',
        opacity,
      }}
    >
      <div className="absolute inset-0" style={{ background: 'radial-gradient(120% 100% at 50% 120%, rgba(0,0,0,.4), transparent 55%)' }} />
    </div>
  );
};

/* ---------- Glass button ---------- */
export interface GlassBtnProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  kind?: 'soft' | 'strong' | 'primary' | 'accent' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
}

export const GlassBtn: React.FC<GlassBtnProps> = ({
  children,
  kind = 'soft',
  size = 'md',
  className = '',
  type = 'button',
  ...rest
}) => {
  const sizeC = size === 'sm' ? 'px-3 py-1.5 text-[11px]' : size === 'lg' ? 'px-5 py-3 text-[13px]' : 'px-4 py-2.5 text-[12px]';
  const kindC = {
    soft: 'glass text-slate-800 dark:text-white/90 rounded-full',
    strong: 'glass-strong text-slate-900 dark:text-white rounded-full',
    primary: 'bg-white text-[#0B0E14] rounded-full font-medium',
    accent: 'rounded-full text-white',
    ghost: 'text-slate-500 dark:text-white/70 hover:text-slate-800 dark:hover:text-white rounded-full',
  }[kind];

  const accentStyle = kind === 'accent'
    ? {
        background: 'linear-gradient(135deg,#7AA3D6,#3E6FA9)',
        boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25), 0 4px 16px rgba(122,163,214,0.4)',
      }
    : undefined;

  return (
    <button type={type} className={`glass-btn ${kindC} ${sizeC} ${className}`} style={accentStyle} {...rest}>
      {children}
    </button>
  );
};

/* ---------- Feature badge (dark) ---------- */
export interface DkBadgeProps {
  kind: '요약' | '일러스트' | '퀴즈' | string;
  size?: 'xs' | 'sm';
}

export const DkBadge: React.FC<DkBadgeProps> = ({ kind, size = 'sm' }) => {
  const map: Record<string, { dot: string }> = {
    '요약':     { dot: '#7AA3D6' },
    '일러스트': { dot: '#E8B86F' },
    '퀴즈':     { dot: '#B5D0EF' },
  };
  const m = map[kind] || { dot: '#7AA3D6' };
  const pad = size === 'xs' ? 'text-[9px] px-1.5 py-[2px]' : 'text-[10px] px-2 py-[3px]';
  return (
    <span className={`inline-flex items-center gap-1 rounded-full glass-soft text-white/75 ${pad}`}>
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.dot, boxShadow: `0 0 4px ${m.dot}` }} />
      {kind}
    </span>
  );
};

/* ---------- Phone frame (with notch + dark wallpaper) ---------- */
export interface FrameProps {
  children: React.ReactNode;
}

export const PhoneFrame: React.FC<FrameProps> = ({ children }) => {
  return (
    <div className="phone-shell">
      <div className="phone-screen dk-surface">
        <div className="dk-grain" />
        <div className="status-bar">
          <span>9:41</span>
          <div className="notch" />
          <div className="flex items-center gap-1">
            <svg width="16" height="10" viewBox="0 0 16 10">
              <path d="M0 8h2v2H0zM4 6h2v4H4zM8 4h2v6H8zM12 2h2v8h-2z" fill="currentColor"/>
            </svg>
            <svg width="16" height="10" viewBox="0 0 16 10">
              <path d="M8 1.5C5 1.5 2.5 3.5 1 5.5l1 1C3.5 4.5 5.5 3 8 3s4.5 1.5 6 3.5l1-1C13.5 3.5 11 1.5 8 1.5zM8 5.5c-1.5 0-2.5.7-3.5 1.5l1 1C6 7.3 7 7 8 7s2 .3 2.5 1l1-1C10.5 6.2 9.5 5.5 8 5.5z" fill="currentColor"/>
            </svg>
            <svg width="22" height="10" viewBox="0 0 24 12">
              <rect x="1" y="1" width="19" height="10" rx="2.5" fill="none" stroke="currentColor"/>
              <rect x="21" y="4" width="2" height="4" rx="1" fill="currentColor"/>
              <rect x="3" y="3" width="14" height="6" rx="1.5" fill="currentColor"/>
            </svg>
          </div>
        </div>
        <div className="absolute inset-0 pt-[44px]">{children}</div>
      </div>
    </div>
  );
};

/* ---------- Web frame ---------- */
export const WebFrame: React.FC<FrameProps> = ({ children }) => {
  return (
    <div className="web-shell">
      <div className="dk-surface absolute inset-0">
        <div className="dk-grain" />
        <div className="web-bar relative">
          <span className="w-2.5 h-2.5 rounded-full bg-[#FF5F57]" />
          <span className="w-2.5 h-2.5 rounded-full bg-[#FEBC2E]" />
          <span className="w-2.5 h-2.5 rounded-full bg-[#28C840]" />
          <div className="ml-6 flex-1 max-w-md mx-auto rounded-md px-3 py-1 text-[11px] text-white/55 glass-soft text-center">
            booktown.kr
          </div>
        </div>
        <div className="absolute inset-0 pt-[36px] overflow-y-auto">{children}</div>
      </div>
    </div>
  );
};

/* ---------- Mobile bottom tab bar (glass) ---------- */
export interface DkTabsProps {
  active: string;
  go: (tab: string) => void;
}

export const DkTabs: React.FC<DkTabsProps> = ({ active, go }) => {
  const items = [
    { id: 'home',    label: '탐색', Icon: DCompass  },
    { id: 'search',  label: '검색', Icon: DSearch   },
    { id: 'history', label: '기록', Icon: DClock    },
    { id: 'me',      label: '마이', Icon: DUser     },
  ];
  const activeTabs: Record<string, string> = {
    detail: 'home',
    summary: 'home',
    illust: 'home',
    quiz: 'home',
  };
  const norm = activeTabs[active] || active;

  return (
    <div className="absolute left-3 right-3 bottom-3 glass-strong rounded-full px-2 py-2 flex items-center justify-around z-40">
      {items.map((it) => {
        const on = norm === it.id;
        return (
          <button
            key={it.id}
            type="button"
            onClick={() => go(it.id)}
            className={`flex-1 flex flex-col items-center gap-0.5 py-1.5 rounded-full transition ${on ? 'text-white' : 'text-white/50'}`}
          >
            <it.Icon className="w-[18px] h-[18px]" />
            <span className={`text-[9px] ${on ? 'font-medium' : ''}`}>{it.label}</span>
          </button>
        );
      })}
    </div>
  );
};

/* ---------- Theme Toggle Button ---------- */
export const ThemeToggle: React.FC = () => {
  const { theme, toggleTheme } = useTheme();
  return (
    <button
      type="button"
      onClick={toggleTheme}
      className="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-slate-700 dark:text-white/70 hover:text-slate-900 dark:hover:text-white transition flex items-center justify-center"
      title={theme === 'light' ? '다크 모드로 전환' : '라이트 모드로 전환'}
      aria-label={theme === 'light' ? '다크 모드로 전환' : '라이트 모드로 전환'}
      aria-pressed={theme === 'dark'}
    >
      {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
    </button>
  );
};

/* ---------- Desktop top nav (glass blur) ---------- */
export interface DkTopNavProps {
  active: string;
  go: (tab: string) => void;
  onLogout?: () => void;
  nickname?: string;
}

export const DkTopNav: React.FC<DkTopNavProps> = ({ active, go, onLogout, nickname = '민' }) => {
  const links = [
    { id: 'home',    label: '탐색' },
    { id: 'search',  label: '검색' },
    { id: 'admin',   label: '관리자' },
  ];
  const activeTabs: Record<string, string> = {
    detail: 'home',
    summary: 'home',
    illust: 'home',
    quiz: 'home',
  };
  const norm = activeTabs[active] || active;

  return (
    <div className="absolute top-0 inset-x-0 z-30 glass-dark border-b border-black/5 dark:border-white/5">
      <div className="h-16 flex items-center px-8 gap-6">
        <button type="button" onClick={() => go('home')} className="flex items-center gap-2.5">
          <img src="/favicon.png" alt="책고을 로고" className="w-6 h-6 object-contain" />
          <span className="font-display text-slate-900 dark:text-white text-[18px] leading-none font-medium">책고을</span>
          <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-slate-400 dark:text-white/40">BOOK · TOWN</span>
        </button>
        <nav className="flex items-center gap-1 ml-4">
          {links.map((l) => (
            <button
              key={l.id}
              type="button"
              onClick={() => go(l.id)}
              className={`px-3 py-1.5 rounded-full text-[12px] transition ${norm === l.id ? 'glass text-slate-900 dark:text-white font-medium' : 'text-slate-500 dark:text-white/55 hover:text-slate-900 dark:hover:text-white'}`}
            >
              {l.label}
            </button>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-3">
          <div className="relative w-[280px]">
            <DSearch className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-white/40" />
            <input
              placeholder="제목 · 저자 · 장르"
              className="w-full glass-soft rounded-full pl-9 pr-3 py-2 text-[12px] text-slate-800 dark:text-white/85 placeholder:text-slate-400 dark:placeholder:text-white/35 focus:outline-none focus:border-black/10 dark:focus:border-white/20"
            />
          </div>
          <ThemeToggle />
          <button
            type="button"
            className="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 text-slate-700 dark:text-white/70 hover:text-slate-900 dark:hover:text-white transition flex items-center justify-center"
            aria-label="알림 확인"
          >
            <DBell className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={() => go('me')}
            className="w-8 h-8 rounded-full bg-black/5 dark:bg-white/10 text-slate-800 dark:text-white text-[12px] grid place-items-center font-medium hover:bg-black/10 dark:hover:bg-white/20 transition"
          >
            {nickname.slice(0, 1)}
          </button>
          {onLogout && (
            <button type="button" onClick={onLogout} className="text-[11px] text-slate-500 dark:text-white/40 hover:text-slate-950 dark:hover:text-white/75 transition ml-1">
              로그아웃
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

/* ---------- Logo mark (small) ---------- */
export interface DkMarkProps {
  size?: number;
}

export const DkMark: React.FC<DkMarkProps> = ({ size = 22 }) => {
  return (
    <div className="flex items-center gap-2">
      <img src="/favicon.png" alt="책고을 로고" style={{ width: size, height: size }} className="object-contain" />
      <span className="font-display text-white leading-none font-medium" style={{ fontSize: size * 0.92 }}>책고을</span>
    </div>
  );
};

/* ---------- Custom Cursor (Glow Light) ---------- */
export const CustomCursor: React.FC = () => {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isVisible, setIsVisible] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setPosition({ x: e.clientX, y: e.clientY });
      if (!isVisible) setIsVisible(true);
    };

    const handleMouseLeave = () => {
      setIsVisible(false);
    };

    const handleMouseEnter = () => {
      setIsVisible(true);
    };

    const handleMouseOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (
        target.tagName === 'BUTTON' ||
        target.tagName === 'A' ||
        target.tagName === 'INPUT' ||
        target.closest('button') ||
        target.closest('a') ||
        target.closest('input') ||
        target.style.cursor === 'pointer'
      ) {
        setIsHovered(true);
      } else {
        setIsHovered(false);
      }
    };

    window.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseleave', handleMouseLeave);
    document.addEventListener('mouseenter', handleMouseEnter);
    window.addEventListener('mouseover', handleMouseOver);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseleave', handleMouseLeave);
      document.removeEventListener('mouseenter', handleMouseEnter);
      window.removeEventListener('mouseover', handleMouseOver);
    };
  }, [isVisible]);

  if (!isVisible) return null;

  return (
    <div
      className="pointer-events-none fixed z-[9999] -translate-x-1/2 -translate-y-1/2 rounded-full transition-transform duration-100 ease-out hidden md:block"
      style={{
        left: position.x,
        top: position.y,
        width: isHovered ? '24px' : '8px',
        height: isHovered ? '24px' : '8px',
        background: 'radial-gradient(circle, rgba(122,163,214,1) 0%, rgba(122,163,214,0.4) 50%, rgba(122,163,214,0) 100%)',
        boxShadow: isHovered 
          ? '0 0 16px rgba(122,163,214,0.8), 0 0 8px rgba(122,163,214,0.4)' 
          : '0 0 8px rgba(122,163,214,0.6)',
        mixBlendMode: 'difference',
      }}
    />
  );
};
