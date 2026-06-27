import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBookById } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import { DkTopNav, DkTabs, DBack, DCheck, DX as DXIcon, DArrow, GlassBtn } from '../components/Primitives';
import { Loader2, AlertCircle } from 'lucide-react';

interface Question {
  q: string;
  choices: string[];
  answer: number;
  explain: string;
}

const MOCK_QUIZZES: Record<string, Question[]> = {
  pride: [
    {
      q: '엘리자베스 베넷은 몇 자매 중 몇째인가요?',
      choices: ['다섯 자매 중 첫째', '다섯 자매 중 둘째', '네 자매 중 둘째', '세 자매 중 막내'],
      answer: 1,
      explain: '베넷 가의 다섯 자매 중 엘리자베스는 둘째입니다.',
    },
    {
      q: '다아시의 첫 청혼 어조로 가장 적절한 것은?',
      choices: ['겸손함', '오만함', '장난스러움', '사무적'],
      answer: 1,
      explain: '첫 청혼에서 다아시는 엘리자베스 가문을 얕보는 오만한 태도를 보입니다.',
    },
    {
      q: '"펨벌리"는 누구의 저택인가요?',
      choices: ['빙리', '콜린스', '다아시', '위컴'],
      answer: 2,
      explain: '펨벌리는 다아시 가문의 저택이며 엘리자베스의 인상이 바뀌는 전환점입니다.',
    },
    {
      q: '리디아와 함께 도망친 인물은?',
      choices: ['다아시', '빙리', '콜린스', '위컴'],
      answer: 3,
      explain: '다아시가 뒤에서 개입해 두 사람을 결혼시키며 베넷 가의 체면을 지킵니다.',
    },
    {
      q: '"Prejudice(편견)"은 주로 누구의 감정을 가리키나요?',
      choices: ['다아시가 베넷 가에 대해', '엘리자베스가 다아시에 대해', '콜린스가 베넷 가에 대해', '빙리 누이가 제인에 대해'],
      answer: 1,
      explain: '엘리자베스의 섣부른 편견을 가리키는 것으로 흔히 해석됩니다.',
    },
  ],
  gatsby: [
    {
      q: '개츠비가 매일 밤 응시하던 초록색 불빛은 어디에 있었나요?',
      choices: ['개츠비의 선착장 끝', '데이지의 집 선착장 끝', '뉴욕 플라자 호텔', '재의 계곡'],
      answer: 1,
      explain: '초록색 불빛은 이스트에그에 있는 데이지의 집 선착장 끝에 켜져 있었습니다.',
    },
    {
      q: '이 소설의 서술자(화자)이자 관찰자는 누구인가요?',
      choices: ['제이 개츠비', '닉 캐러웨이', '톰 뷰캐넌', '조지 윌슨'],
      answer: 1,
      explain: '닉 캐러웨이가 이야기의 서술자로서 개츠비의 삶을 우리에게 전달합니다.',
    },
    {
      q: '개츠비가 엄청난 부를 모은 주된 진짜 목적은 무엇인가요?',
      choices: ['데이지의 사랑을 되찾기 위해', '상류사회 일원이 되기 위해', '가난했던 어린 시절을 보상받기 위해', '정치인이 되기 위해'],
      answer: 0,
      explain: '개츠비는 오직 잃어버린 옛 사랑 데이지를 되찾고 그녀의 수준에 맞추기 위해 부를 축적했습니다.',
    },
    {
      q: '개츠비의 원래 본명은 무엇인가요?',
      choices: ['닉 캐러웨이', '제임스 개츠', '제이 게리', '코디 개츠'],
      answer: 1,
      explain: '그의 본명은 제임스 개츠(James Gatz)였으나, 성공을 꿈꾸며 제이 개츠비로 개명했습니다.',
    },
    {
      q: '데이지의 남편 톰 뷰캐넌의 성격으로 가장 적절한 것은?',
      choices: ['헌신적이고 자상함', '이기적이고 권력지향적임', '순진하고 어리숙함', '예술적이고 감성적임'],
      answer: 1,
      explain: '톰은 부유한 상류층으로서 이기적이고 폭력적이며 자신의 안위만을 챙기는 인물입니다.',
    },
  ],
  miserables: [
    {
      q: '장발장이 빵 한 조각을 훔치고 복역한 총 기간은 몇 년인가요?',
      choices: ['5년', '19년', '10년', '15년'],
      answer: 1,
      explain: '장발장은 빵 한 조각을 훔친 죄로 5년을 선고받았으나 탈옥 시도들로 인해 총 19년간 복역했습니다.',
    },
    {
      q: '장발장에게 은식기를 건네주며 그의 영혼을 구원한 인물은?',
      choices: ['코제트', '미리엘 주교', '자베르 경감', '판틴'],
      answer: 1,
      explain: '미리엘 주교는 장발장이 은식기를 훔쳤음에도 은촛대까지 얹어주며 그에게 새로운 삶을 살도록 감화시켰습니다.',
    },
    {
      q: '평생 장발장을 끈질기게 추적하는 집념의 수사관은?',
      choices: ['마리우스', '자베르', '떼나르디에', '포슐르방'],
      answer: 1,
      explain: '자베르 경감은 법과 정의만을 신봉하며 평생 장발장의 뒤를 쫓는 인물입니다.',
    },
    {
      q: '코제트의 어머니이자 장발장이 임종을 지키며 돌보겠다고 약속한 여인은?',
      choices: ['에포닌', '판틴', '젤루즈', '심플리스'],
      answer: 1,
      explain: '판틴은 딸 코제트를 위해 자신을 희생한 비극적 여인이며, 장발장은 그녀의 사후 코제트를 친딸처럼 양육합니다.',
    },
    {
      q: '소설의 결말에서 장발장이 마리우스를 구출해 낸 장소는 어디인가요?',
      choices: ['파리의 하수구', '바리케이드 요새', '센 강변', '룩셈부르크 공원'],
      answer: 0,
      explain: '장발장은 혁명 바리케이드에서 부상당한 마리우스를 짊어지고 파리의 어두운 하수구를 통해 극적으로 탈출합니다.',
    },
  ],
  jane: [
    {
      q: '제인 에어가 유년 시절 학대를 받으며 자란 친척 집의 이름은 무엇인가요?',
      choices: ['손필드 저택', '게이츠헤드 홀', '로우드 학교', '무어 하우스'],
      answer: 1,
      explain: '제인은 외숙모 리드 부인과 사촌들로부터 게이츠헤드 홀에서 극심한 정신적, 신체적 학대를 받았습니다.',
    },
    {
      q: '제인 에어가 손필드 저택에서 만난, 비밀을 간직한 인물이자 격정적인 사랑을 나누는 주인은?',
      choices: ['싱클레어', '로체스터', '세인트 존', '블록클허스트'],
      answer: 1,
      explain: '제인은 에드워드 로체스터 씨의 가정교사로 들어가 그와 깊은 사랑에 빠집니다.',
    },
    {
      q: '로체스터가 제인과의 결혼식에서 숨기고 있던 충격적인 비밀은 무엇인가요?',
      choices: ['전과자였다는 사실', '손필드 다락방에 미친 아내가 있다는 사실', '파산 상태라는 사실', '데이지라는 다른 약혼녀가 있다는 사실'],
      answer: 1,
      explain: '로체스터는 다락방에 광증을 앓는 아내 버사 메이슨을 감금하고 있었다는 사실이 식장에서 폭로되었습니다.',
    },
    {
      q: '손필드를 떠나 방황하던 제인에게 일자리와 안식처를 주고 나중에 청혼까지 하는 목사의 이름은?',
      choices: ['에드워드 로체스터', '세인트 존 리버스', '블록클허스트', '로이드'],
      answer: 1,
      explain: '목사 세인트 존 리버스는 갈 곳 없는 제인을 구해주고 함께 인도로 선교 활동을 가자며 청혼합니다.',
    },
    {
      q: '제인이 마침내 로체스터에게 돌아왔을 때, 로체스터는 어떤 상태였나요?',
      choices: ['다른 여자와 행복하게 살고 있었다', '화재로 시력을 잃고 한쪽 손을 다친 상태였다', '이미 세상을 떠난 뒤였다', '제인을 알아보지 못하고 미쳐 있었다'],
      answer: 1,
      explain: '아내 버사가 지른 화재로 손필드는 전소되었으며, 로체스터는 아내를 구하려다 시력과 한쪽 손을 잃고 홀로 지내고 있었습니다.',
    },
  ],
};

const DEFAULT_QUIZ: Question[] = [
  {
    q: '이 도서의 핵심 주제로 가장 알맞은 것은 무엇인가요?',
    choices: ['인간의 실존적 고뇌와 구원', '신분 질서의 파괴와 자유의 획득', '산업화 속 소외되는 대중의 초상', '현대 자본주의 사회의 구조적 갈등'],
    answer: 0,
    explain: '이 고전 작품은 인물의 한계 극복과 도덕적 구원이라는 보편적 실존 주제를 담고 있습니다.',
  },
  {
    q: '주인공이 직면한 갈등의 주된 원인은 무엇인가요?',
    choices: ['지배층과 피지배층의 계급 갈등', '사회적 통념 및 도덕적 기준과의 불일치', '개인 내부의 도덕적 결함과 자아 분열', '급격한 시대적 변화에 따른 세대 간 격차'],
    answer: 1,
    explain: '사회의 낡은 질서와 법률, 그리고 주인공의 고결한 이성이 충돌하면서 파생되는 외적 갈등이 핵심 줄거리입니다.',
  },
  {
    q: '작품의 배경이 되는 공간적 특징은 어떤 상징성을 지녔나요?',
    choices: ['산업적 화려함และ 도덕적 황폐함의 대비', '고귀한 안식처이자 속박의 굴레', '미지의 개척지이자 투쟁의 역사', '전통적 가치관이 붕괴되는 도시 문명'],
    answer: 1,
    explain: '작가가 설정한 주요 공간은 주인공에게 안식처를 주는 동시에, 극복해야 할 사회적 제약을 대변합니다.',
  },
  {
    q: '결말부에서 드러나는 작가의 메시지로 가장 알맞은 것은 무엇인가요?',
    choices: ['비극을 통한 연대 의식의 고취', '운명의 한계를 받아들이는 순응적 태도', '진정한 용서와 상생을 향한 화해', '소시민들의 이기심과 사회 부조리 폭로'],
    answer: 2,
    explain: '모든 파국적 갈등이 봉합되고 인물 간의 화해와 역사적 성찰이 이루어지는 휴머니즘적 가치관을 강조합니다.',
  },
  {
    q: '이 작품의 시점과 서술 방식의 특징으로 적절한 것은?',
    choices: ['1인칭 주인공 시점으로 극도의 주관성을 띤다', '전지적 작가 시점으로 인물의 심리를 입체적으로 추적한다', '관찰자의 눈을 빌려 객관적인 서사만을 전달한다', '여러 화자가 등장하는 다성적 서사 구조를 갖는다'],
    answer: 1,
    explain: '서술자는 각 인물의 내밀한 욕망과 고뇌를 전지적 시점에서 포착하여 풍성한 독서 경험을 돕습니다.',
  },
];

interface ScoreRingProps {
  score: number;
  total: number;
  size?: number;
}

const ScoreRing: React.FC<ScoreRingProps> = ({ score, total, size = 96 }) => {
  const pct = score / total;
  const r = size / 2 - 8;
  const circ = 2 * Math.PI * r;

  let strokeColor = '#D46A6A';
  if (pct >= 0.8) {
    strokeColor = '#5FC9A0';
  } else if (pct >= 0.6) {
    strokeColor = '#E8B86F';
  }

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

const QuizLayout: React.FC<QuizLayoutProps> = ({
  book,
  subtitle,
  activeTab,
  mobileContent,
  desktopContent,
}) => {
  const navigate = useNavigate();
  const { user, logout, isAuthenticated } = useAuth();

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
      <div className="md:hidden flex-1 flex flex-col">
        <div className="h-[52px] px-4 flex items-center justify-between border-b border-white/5 relative z-10 shrink-0 bg-[#0F0E13]/80 backdrop-blur-md">
          <div className="flex items-center gap-3">
            <button 
              onClick={() => navigate(`/books/${book.id}`)} 
              className="text-white/45 active:text-white transition"
              aria-label="상세 페이지로 이동"
            >
              <DBack className="w-5 h-5" />
            </button>
            <div>
              <div className="text-[14px] leading-tight font-bold">{book.title}</div>
              <div className="text-[9px] text-white/35 font-mono uppercase tracking-wider">{subtitle}</div>
            </div>
          </div>
          <button onClick={handleLogout} className="text-[10px] font-mono uppercase tracking-[0.14em] text-white/45 hover:text-white transition">
            LOGOUT
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 pb-[96px] pt-4">
          {mobileContent}
        </div>
        <DkTabs active={activeTab} go={handleGo} />
      </div>

      {/* Web/Desktop view */}
      <div className="hidden md:flex flex-col flex-1 relative">
        <DkTopNav 
          active="home" 
          go={handleGo} 
          onLogout={isAuthenticated ? handleLogout : undefined} 
          nickname={user?.nickname || '민'} 
        />
        <div className="flex-1 overflow-y-auto pt-24 px-10 py-8 max-w-6xl mx-auto w-full">
          {desktopContent}
        </div>
      </div>
    </div>
  );
};

export const QuizPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { isMockMode } = useAuth();
  const navigate = useNavigate();

  const [book, setBook] = useState<Book | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [i, setI] = useState(0);
  const [picked, setPicked] = useState<number | null>(null);
  const [reveal, setReveal] = useState(false);
  const [score, setScore] = useState(0);
  const [answers, setAnswers] = useState<Array<{ q: string; correct: string; chosen: string; ok: boolean }>>([]);

  useEffect(() => {
    if (!bookId) return;
    const fetchBook = async () => {
      setLoading(true);
      try {
        const data = await getBookById(isMockMode, bookId);
        setBook(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : '도서 정보를 가져오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };
    fetchBook();
  }, [bookId, isMockMode]);

  if (loading) return <div className="min-h-screen dk-surface flex items-center justify-center"><Loader2 className="w-10 h-10 text-purple-500 animate-spin" /></div>;
  if (error || !book) return <div className="min-h-screen dk-surface flex items-center justify-center p-6"><div className="glass p-8 rounded-2xl text-center"><AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-4" /><p className="text-white/70">{error || '도서를 찾을 수 없습니다.'}</p></div></div>;

  const questions = MOCK_QUIZZES[book.id] || DEFAULT_QUIZ;
  const q = questions[i];
  const pct = Math.round((i / questions.length) * 100);

  const pick = (idx: number) => {
    if (reveal) return;
    setPicked(idx);
    setReveal(true);
    const isCorrect = idx === q.answer;
    if (isCorrect) setScore((s) => s + 1);
    setAnswers((prev) => [...prev, { q: q.q, correct: q.choices[q.answer], chosen: q.choices[idx], ok: isCorrect }]);
  };

  const next = () => {
    if (i === questions.length - 1) {
      navigate(`/books/${book.id}/quiz/result`, { state: { score, total: questions.length, answers } });
      return;
    }
    setI(i + 1);
    setPicked(null);
    setReveal(false);
  };

  const getChoiceIcon = (isCorrect: boolean, isWrong: boolean, idx: number) => {
    if (isCorrect) return <DCheck className="w-3.5 h-3.5" />;
    if (isWrong) return <DXIcon className="w-3.5 h-3.5" />;
    return String.fromCodePoint(65 + idx);
  };

  const renderQuizBody = () => (
    <>
      <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider mb-2 text-white/70">
        <span>{i + 1} / {questions.length}</span>
        <span className="text-white/45">SCORE {score}</span>
      </div>
      <div className="h-[2px] bg-white/10 rounded-full overflow-hidden">
        <div className="h-full bg-white transition-[width] duration-300" style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-5 glass rounded-2xl p-5 border border-white/5 relative z-10">
        <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">Q{i + 1}</div>
        <div className="font-display text-[20px] text-white leading-[1.4] mt-1.5 font-bold">{q.q}</div>
        <div className="mt-4 space-y-2">
          {q.choices.map((c, idx) => {
            const isCorrect = reveal && idx === q.answer;
            const isWrong = reveal && idx === picked && picked !== q.answer;
            const ring = isCorrect ? 'border-[#5FC9A0] bg-[#5FC9A0]/10 text-white' : isWrong ? 'border-[#D46A6A] bg-[#D46A6A]/10 text-white' : picked === idx ? 'border-white/30 bg-white/5 text-white' : 'border-white/10 hover:border-white/25 text-white/90';
            
            let circleBg = 'glass-soft text-white/70';
            if (isCorrect) {
              circleBg = 'bg-[#5FC9A0] text-white';
            } else if (isWrong) {
              circleBg = 'bg-[#D46A6A] text-white';
            }

            return (
              <button key={c} onClick={() => pick(idx)} className={`w-full text-left flex items-center gap-3 px-3.5 py-3 rounded-xl border transition ${ring}`}>
                <div className={`w-6 h-6 rounded-full grid place-items-center text-[10px] font-mono shrink-0 ${circleBg}`}>
                  {getChoiceIcon(isCorrect, isWrong, idx)}
                </div>
                <div className="text-[12px] flex-1">{c}</div>
              </button>
            );
          })}
        </div>
        {reveal && <div className={`mt-4 rounded-xl p-3.5 ${picked === q.answer ? 'glass border-l-2 border-[#5FC9A0]' : 'glass-soft border-l-2 border-[#D46A6A]'}`}><div className={`font-mono text-[10px] uppercase tracking-wider ${picked === q.answer ? 'text-[#5FC9A0]' : 'text-[#D46A6A]'}`}>{picked === q.answer ? '정답 · CORRECT' : '오답 · INCORRECT'}</div><div className="text-[11px] text-white/75 leading-[1.6] mt-1.5 font-light">{q.explain}</div></div>}
      </div>
      <div className="mt-6 flex items-center justify-end">
        <GlassBtn kind={reveal ? 'primary' : 'soft'} size="md" onClick={reveal ? next : undefined} disabled={!reveal} className="flex items-center gap-1.5">
          {i === questions.length - 1 ? '결과 보기' : '다음 문제'} <DArrow className="w-3.5 h-3.5" />
        </GlassBtn>
      </div>
    </>
  );

  return (
    <QuizLayout book={book} subtitle="Chapter 1–3 Quiz" activeTab="quiz" mobileContent={<div><div className="font-display text-[22px] font-bold text-white mb-0.5 leading-tight">{book.title}</div><div className="text-[10px] text-white/45 font-mono uppercase tracking-wider mb-5">5 QUESTIONS · ~2 MIN</div>{renderQuizBody()}</div>} desktopContent={<><button onClick={() => navigate(`/books/${book.id}`)} className="text-[11px] font-mono uppercase tracking-[0.14em] text-white/45 hover:text-white flex items-center gap-1 transition"><DBack className="w-3.5 h-3.5" /> BACK TO DETAIL</button><div className="grid grid-cols-[1fr_300px] gap-10 mt-6"><div><div className="font-mono text-[10px] uppercase tracking-[0.22em] text-white/45">COMPREHENSION QUIZ</div><div className="font-display text-[44px] text-white leading-[1] mt-2 font-bold">{book.title}</div><div className="text-[12px] text-white/45 mt-1 font-mono uppercase tracking-wider mb-8">5 QUESTIONS · CH 1–3</div><div className="max-w-[640px]">{renderQuizBody()}</div></div><aside className="space-y-3"><div className="glass rounded-2xl p-5 border border-white/5"><div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">퀴즈 진행률</div><div className="flex items-end gap-1 mt-2"><div className="font-display text-[44px] leading-none font-bold">{pct}</div><div className="text-[10px] text-white/45 mb-1.5 font-mono">%</div></div><div className="mt-3 h-[2px] bg-white/10 rounded-full overflow-hidden"><div className="h-full bg-white" style={{ width: `${pct}%` }} /></div></div></aside></div></>} />
  );
};

export const QuizResultPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { isMockMode } = useAuth();
  const [book, setBook] = useState<Book | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const state = location.state as { score?: number; total?: number; answers?: Array<{ q: string; correct: string; chosen: string; ok: boolean }> } | null;

  useEffect(() => { if (!state) navigate(`/books/${bookId}`, { replace: true }); }, [state, navigate, bookId]);
  useEffect(() => { 
    if (bookId) {
      getBookById(isMockMode, bookId)
        .then(setBook)
        .catch((err) => {
          console.error(err);
          setError('도서 정보를 불러오는데 실패했습니다.');
        })
        .finally(() => setLoading(false));
    } 
  }, [bookId, isMockMode]);

  if (error) {
    return (
      <div className="min-h-screen dk-surface flex flex-col items-center justify-center text-center p-6 relative select-none">
        <div className="dk-grain absolute inset-0 opacity-40 pointer-events-none" />
        <div className="glass rounded-2xl p-8 max-w-md border border-white/5 z-10">
          <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-white mb-2">오류가 발생했습니다</h2>
          <p className="text-white/60 text-sm mb-6">{error}</p>
          <button 
            onClick={() => navigate(`/books/${bookId}`)} 
            className="px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-full text-sm font-medium transition"
          >
            도서 상세로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  if (loading || !book || !state) return <div className="min-h-screen dk-surface flex items-center justify-center"><Loader2 className="w-10 h-10 text-purple-500 animate-spin" /></div>;

  const score = state.score ?? 0;
  const total = state.total ?? 5;
  const answers = state.answers ?? [];

  let titleText = '다시 도전!';
  if (score >= 4) {
    titleText = '훌륭해요!';
  } else if (score >= 3) {
    titleText = '잘 했어요';
  }

  return (
    <QuizLayout
      book={book}
      subtitle="Quiz Report"
      activeTab="me"
      mobileContent={
        <div>
          {/* Score card */}
          <div className="glass rounded-2xl p-5 mt-2 flex items-center gap-5 border border-white/5">
            <div className="relative shrink-0">
              <ScoreRing score={score} total={total} size={88} />
              <div className="absolute inset-0 grid place-items-center">
                <div className="text-center">
                  <div className="font-display text-[26px] leading-none text-white font-bold">{score}</div>
                  <div className="text-[10px] text-white/40 font-mono">/ {total}</div>
                </div>
              </div>
            </div>
            <div>
              <div className="font-display text-[22px] leading-[1.1] font-bold text-white">{titleText}</div>
              <div className="text-[12px] text-white/55 mt-1.5 font-light">{book.title} · CH 1–3 · 정답률 {Math.round((score / total) * 100)}%</div>
              <div className="text-[10px] font-mono text-white/35 mt-1">STANDARD · 소요 3분 21초</div>
            </div>
          </div>

          {/* Breakdown */}
          <div className="glass rounded-2xl p-5 mt-4 border border-white/5">
            <div className="font-display text-[16px] mb-4 font-bold">문항별 채점 결과</div>
            <div className="space-y-4">
              {answers.map((ans, idx) => (
                <div key={ans.q} className="border-b border-white/5 pb-4 last:border-0 last:pb-0">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-5 h-5 rounded-full grid place-items-center text-[10px] font-mono font-bold shrink-0 ${
                          ans.ok ? 'bg-[#5FC9A0] text-white' : 'bg-[#D46A6A] text-white'
                        }`}
                      >
                        {idx + 1}
                      </span>
                      <div className="text-[12px] font-medium leading-snug">{ans.q}</div>
                    </div>
                    <span className={`text-[10px] font-bold shrink-0 ${ans.ok ? 'text-[#5FC9A0]' : 'text-[#D46A6A]'}`}>
                      {ans.ok ? '정답' : '오답'}
                    </span>
                  </div>
                  <div className="mt-2 pl-7 text-[11px] text-white/55 space-y-1 font-light">
                    <div>
                      <span className="text-white/30 mr-1">내가 선택한 답:</span>
                      {ans.chosen}
                    </div>
                    {!ans.ok && (
                      <div>
                        <span className="text-white/30 mr-1">올바른 정답:</span>
                        {ans.correct}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Actions */}
          <div className="flex gap-2 mt-4 z-10 relative">
            <button
              onClick={() => navigate(`/books/${book.id}/quiz`)}
              className="flex-1 py-3 glass-soft rounded-full text-[12px] text-white/75 text-center active:scale-[0.98] transition"
            >
              다시 풀기
            </button>
            <button
              onClick={() => navigate(`/books/${book.id}`)}
              className="flex-1 py-3 rounded-full text-[12px] text-white font-medium text-center active:scale-[0.98] transition"
              style={{ background: 'linear-gradient(135deg,#7AA3D6,#3E6FA9)' }}
            >
              상세로 돌아가기
            </button>
          </div>
        </div>
      }
      desktopContent={
        <>
          <button
            onClick={() => navigate(`/books/${book.id}/quiz`)}
            className="text-[10px] font-mono uppercase tracking-[0.14em] text-white/35 hover:text-white flex items-center gap-1 transition mb-6"
          >
            <DBack className="w-3 h-3" /> RESTART QUIZ
          </button>
          <div className="grid grid-cols-[1fr_380px] gap-8">
            <div className="space-y-5">
              {/* Score ring box */}
              <div className="glass rounded-2xl p-7 flex items-center gap-8 border border-white/5">
                <div className="relative shrink-0">
                  <ScoreRing score={score} total={total} size={120} />
                  <div className="absolute inset-0 grid place-items-center">
                    <div className="text-center">
                      <div className="font-display text-[32px] leading-none text-white font-bold">{score}</div>
                      <div className="text-[12px] text-white/40 font-mono">/ {total}</div>
                    </div>
                  </div>
                </div>
                <div>
                  <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">COMPREHENSION SCORE</div>
                  <div className="font-display text-[32px] text-white leading-none mt-2.5 font-bold">{titleText}</div>
                  <div className="text-[13px] text-white/55 mt-2.5 font-light">
                    도서 <span className="font-semibold text-white">『{book.title}』</span> 퀴즈 결과 정답률 <span className="font-semibold text-white">{Math.round((score / total) * 100)}%</span>를 기록하였습니다.
                  </div>
                </div>
              </div>

              {/* Detailed scorecard */}
              <div className="glass rounded-2xl p-6 border border-white/5">
                <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45 mb-4">문항별 결과 상세 리포트</div>
                <div className="space-y-5">
                  {answers.map((ans, idx) => (
                    <div key={ans.q} className="border-b border-white/5 pb-4 last:border-0 last:pb-0">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex items-start gap-3">
                          <span
                            className={`w-6 h-6 rounded-full grid place-items-center text-[11px] font-mono font-bold shrink-0 mt-0.5 ${
                              ans.ok ? 'bg-[#5FC9A0] text-white' : 'bg-[#D46A6A] text-white'
                            }`}
                          >
                            {idx + 1}
                          </span>
                          <div>
                            <div className="text-[13px] font-medium leading-snug text-white/90">{ans.q}</div>
                          </div>
                        </div>
                        <span className={`text-[11px] font-bold shrink-0 ${ans.ok ? 'text-[#5FC9A0]' : 'text-[#D46A6A]'}`}>
                          {ans.ok ? '정답 · CORRECT' : '오답 · INCORRECT'}
                        </span>
                      </div>
                      <div className="mt-2 pl-9 text-[12px] text-white/55 space-y-1 font-light">
                        <div>
                          <span className="text-white/35 mr-1">내가 선택한 답:</span>
                          {ans.chosen}
                        </div>
                        {!ans.ok && (
                          <div>
                            <span className="text-white/35 mr-1">올바른 정답:</span>
                            {ans.correct}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Sidebar statistics */}
            <aside className="space-y-4">
              <div className="glass rounded-2xl p-5 border border-white/5">
                <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">학습 제안</div>
                <div className="text-[18px] font-display mt-2 font-semibold">
                  {score >= 4 ? '다음 챕터로 진행하세요!' : '해당 챕터를 복습해 보세요.'}
                </div>
                <p className="text-[12px] text-white/55 mt-1.5 font-light leading-relaxed">
                  {score >= 4
                    ? '이해도가 매우 높습니다. 퀴즈를 무사히 마쳤으니 이제 다음 챕터의 AI 요약본이나 삽화 갤러리를 확인해 보시는 것을 추천합니다.'
                    : '오답 해설을 꼼꼼히 확인하고, 필요한 경우 해당 챕터의 요약본을 다시 한 번 읽어보시는 것을 권장합니다.'}
                </p>
              </div>
            </aside>
          </div>
        </>
      }
    />
  );
};
