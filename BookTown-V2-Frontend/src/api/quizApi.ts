import client from './client';
import { MOCK_BOOKS } from './bookApi';

export type QuizDifficulty = 'EASY' | 'NORMAL' | 'HARD';
export type QuizJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface CreateQuizRequest {
  chapterFrom: number;
  chapterTo: number;
  questionCount: number;
  difficulty: QuizDifficulty;
}

export interface QuizJob {
  jobId: string;
  status: QuizJobStatus;
  quizId?: string;
  message?: string;
}

export interface QuizQuestion {
  id: string;
  text: string;
  options: Array<{
    id: string;
    text: string;
  }>;
}

export interface Quiz {
  id: string;
  bookId: string;
  title: string;
  chapterRange: string;
  difficulty: QuizDifficulty;
  questions: QuizQuestion[];
}

export interface SubmitQuizRequest {
  answers: Array<{
    questionId: string;
    optionId: string;
  }>;
}

export interface QuizSubmissionResult {
  quizId: string;
  score: number;
  total: number;
  accuracy: number;
  submittedAt: string;
  items: Array<{
    questionId: string;
    question: string;
    chosen: string;
    correct: string;
    explanation: string;
    isCorrect: boolean;
  }>;
}

export interface QuizHistoryItem {
  quizId: string;
  bookId: string;
  bookTitle: string;
  score: number;
  total: number;
  accuracy: number;
  submittedAt: string;
}

interface ApiResponse<T> {
  data: T;
  meta?: unknown;
}

interface ApiQuestionDto {
  id: number | string;
  question?: string;
  text?: string;
  content?: string;
  options?: Array<{
    id: number | string;
    optionText?: string;
    text?: string;
    content?: string;
  }>;
  choices?: Array<{
    id: number | string;
    optionText?: string;
    text?: string;
    content?: string;
  }>;
}

interface ApiQuizDto {
  id: number | string;
  bookId?: number | string;
  title?: string;
  chapterRange?: string;
  difficulty?: QuizDifficulty;
  questions: ApiQuestionDto[];
}

interface ApiResultItemDto {
  questionId: number | string;
  question?: string;
  questionText?: string;
  chosen?: string;
  chosenText?: string;
  selectedOptionText?: string;
  correct?: string;
  correctText?: string;
  correctOptionText?: string;
  explanation?: string;
  isCorrect?: boolean;
  correctAnswer?: boolean;
}

interface ApiSubmissionResultDto {
  quizId: number | string;
  score: number;
  total?: number;
  accuracy?: number;
  submittedAt?: string;
  results?: ApiResultItemDto[];
  items?: ApiResultItemDto[];
}

interface MockQuestionSeed {
  q: string;
  choices: string[];
  answer: number;
  explain: string;
}

const MOCK_QUIZZES: Record<string, MockQuestionSeed[]> = {
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
      q: '이 소설의 서술자이자 관찰자는 누구인가요?',
      choices: ['제이 개츠비', '닉 캐러웨이', '톰 뷰캐넌', '조지 윌슨'],
      answer: 1,
      explain: '닉 캐러웨이가 이야기의 서술자로서 개츠비의 삶을 전달합니다.',
    },
    {
      q: '개츠비가 부를 모은 주된 목적은 무엇인가요?',
      choices: ['데이지의 사랑을 되찾기 위해', '상류사회 일원이 되기 위해', '가난했던 어린 시절을 보상받기 위해', '정치인이 되기 위해'],
      answer: 0,
      explain: '개츠비는 잃어버린 사랑 데이지를 되찾기 위해 부를 축적했습니다.',
    },
    {
      q: '개츠비의 원래 본명은 무엇인가요?',
      choices: ['닉 캐러웨이', '제임스 개츠', '제이 게리', '코디 개츠'],
      answer: 1,
      explain: '그의 본명은 제임스 개츠였으나 성공을 꿈꾸며 제이 개츠비로 개명했습니다.',
    },
    {
      q: '톰 뷰캐넌의 성격으로 가장 적절한 것은?',
      choices: ['헌신적이고 자상함', '이기적이고 권력지향적임', '순진하고 어리숙함', '예술적이고 감성적임'],
      answer: 1,
      explain: '톰은 부유한 상류층으로서 이기적이고 권력지향적인 인물입니다.',
    },
  ],
};

const DEFAULT_QUIZ: MockQuestionSeed[] = [
  {
    q: '이 도서의 핵심 주제로 가장 알맞은 것은 무엇인가요?',
    choices: ['인간의 실존적 고뇌와 구원', '신분 질서의 파괴와 자유의 획득', '산업화 속 소외되는 대중의 초상', '현대 자본주의 사회의 구조적 갈등'],
    answer: 0,
    explain: '이 고전 작품은 인물의 한계 극복과 도덕적 구원이라는 보편적 주제를 담고 있습니다.',
  },
  {
    q: '주인공이 직면한 갈등의 주된 원인은 무엇인가요?',
    choices: ['지배층과 피지배층의 계급 갈등', '사회적 통념 및 도덕적 기준과의 불일치', '개인 내부의 도덕적 결함과 자아 분열', '급격한 시대적 변화에 따른 세대 간 격차'],
    answer: 1,
    explain: '사회의 낡은 질서와 주인공의 판단이 충돌하면서 외적 갈등이 커집니다.',
  },
  {
    q: '작품의 배경이 되는 공간적 특징은 어떤 상징성을 지녔나요?',
    choices: ['산업적 화려함과 도덕적 황폐함의 대비', '고귀한 안식처이자 속박의 굴레', '미지의 개척지이자 투쟁의 역사', '전통적 가치관이 붕괴되는 도시 문명'],
    answer: 1,
    explain: '주요 공간은 안식처인 동시에 극복해야 할 사회적 제약을 대변합니다.',
  },
  {
    q: '결말부에서 드러나는 작가의 메시지로 가장 알맞은 것은 무엇인가요?',
    choices: ['비극을 통한 연대 의식의 고취', '운명의 한계를 받아들이는 순응적 태도', '진정한 용서와 상생을 향한 화해', '소시민들의 이기심과 사회 부조리 폭로'],
    answer: 2,
    explain: '갈등의 봉합과 인물 간 화해를 통해 휴머니즘적 가치관을 강조합니다.',
  },
  {
    q: '이 작품의 시점과 서술 방식의 특징으로 적절한 것은?',
    choices: ['1인칭 주인공 시점으로 극도의 주관성을 띤다', '전지적 작가 시점으로 인물의 심리를 입체적으로 추적한다', '관찰자의 눈을 빌려 객관적인 서사만을 전달한다', '여러 화자가 등장하는 다성적 서사 구조를 갖는다'],
    answer: 1,
    explain: '서술자는 각 인물의 욕망과 고뇌를 전지적 시점에서 포착합니다.',
  },
];

const mockQuizStore = new Map<string, { quiz: Quiz; seeds: MockQuestionSeed[] }>();

const unwrap = <T>(value: ApiResponse<T> | T): T => {
  if (value && typeof value === 'object' && 'data' in value) {
    return (value as ApiResponse<T>).data;
  }
  return value as T;
};

const toQuiz = (dto: ApiQuizDto, fallbackBookId: string): Quiz => ({
  id: String(dto.id),
  bookId: String(dto.bookId ?? fallbackBookId),
  title: dto.title ?? '객관식 이해도 퀴즈',
  chapterRange: dto.chapterRange ?? '선택한 챕터',
  difficulty: dto.difficulty ?? 'NORMAL',
  questions: dto.questions.map((question) => {
    const options = question.options ?? question.choices ?? [];
    return {
      id: String(question.id),
      text: question.question ?? question.text ?? question.content ?? '',
      options: options.map((option) => ({
        id: String(option.id),
        text: option.optionText ?? option.text ?? option.content ?? '',
      })),
    };
  }),
});

const toResult = (dto: ApiSubmissionResultDto): QuizSubmissionResult => {
  const items = dto.items ?? dto.results ?? [];
  const total = dto.total ?? items.length;
  return {
    quizId: String(dto.quizId),
    score: dto.score,
    total,
    accuracy: dto.accuracy ?? (total > 0 ? Math.round((dto.score / total) * 100) : 0),
    submittedAt: dto.submittedAt ?? new Date().toISOString(),
    items: items.map((item) => ({
      questionId: String(item.questionId),
      question: item.question ?? item.questionText ?? '',
      chosen: item.chosen ?? item.chosenText ?? item.selectedOptionText ?? '',
      correct: item.correct ?? item.correctText ?? item.correctOptionText ?? '',
      explanation: item.explanation ?? '',
      isCorrect: Boolean(item.isCorrect ?? item.correctAnswer),
    })),
  };
};

const getMockSeeds = (bookId: string, questionCount: number) => {
  const seeds = MOCK_QUIZZES[bookId] ?? DEFAULT_QUIZ;
  return seeds.slice(0, questionCount);
};

const makeMockQuiz = (bookId: string, request: CreateQuizRequest): Quiz => {
  const book = MOCK_BOOKS.find((item) => item.id === bookId);
  const seeds = getMockSeeds(bookId, request.questionCount);
  const quizId = `mock-${bookId}-${Date.now()}`;
  const quiz: Quiz = {
    id: quizId,
    bookId,
    title: `${book?.title ?? '도서'} 이해도 퀴즈`,
    chapterRange: `${request.chapterFrom}장 - ${request.chapterTo}장`,
    difficulty: request.difficulty,
    questions: seeds.map((seed, questionIndex) => ({
      id: `${quizId}-q-${questionIndex + 1}`,
      text: seed.q,
      options: seed.choices.map((choice, optionIndex) => ({
        id: `${quizId}-q-${questionIndex + 1}-o-${optionIndex}`,
        text: choice,
      })),
    })),
  };
  mockQuizStore.set(quizId, { quiz, seeds });
  return quiz;
};

export const createQuiz = async (
  isMockMode: boolean,
  bookId: string,
  request: CreateQuizRequest
): Promise<QuizJob> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    const quiz = makeMockQuiz(bookId, request);
    return {
      jobId: `mock-job-${quiz.id}`,
      status: 'QUEUED',
      quizId: quiz.id,
      message: '퀴즈 생성 요청을 접수했습니다.',
    };
  }

  const res = await client.post<ApiResponse<QuizJob> | QuizJob>(`/books/${bookId}/quizzes`, request);
  return unwrap(res.data);
};

export const getQuizJob = async (isMockMode: boolean, jobId: string): Promise<QuizJob> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 700));
    return {
      jobId,
      status: 'COMPLETED',
      quizId: jobId.replace('mock-job-', ''),
      message: '퀴즈 생성이 완료되었습니다.',
    };
  }

  const res = await client.get<ApiResponse<QuizJob> | QuizJob>(`/quiz-jobs/${jobId}`);
  return unwrap(res.data);
};

export const getQuiz = async (
  isMockMode: boolean,
  bookId: string,
  quizId: string
): Promise<Quiz> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    const stored = mockQuizStore.get(quizId);
    if (!stored) throw new Error('퀴즈를 찾을 수 없습니다.');
    return stored.quiz;
  }

  const res = await client.get<ApiResponse<ApiQuizDto> | ApiQuizDto>(`/quizzes/${quizId}`);
  return toQuiz(unwrap(res.data), bookId);
};

export const submitQuiz = async (
  isMockMode: boolean,
  quizId: string,
  request: SubmitQuizRequest
): Promise<QuizSubmissionResult> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    const stored = mockQuizStore.get(quizId);
    if (!stored) throw new Error('퀴즈를 찾을 수 없습니다.');
    const items = stored.quiz.questions.map((question, index) => {
      const seed = stored.seeds[index];
      const answer = request.answers.find((item) => item.questionId === question.id);
      const chosenOption = question.options.find((option) => option.id === answer?.optionId);
      const correctOption = question.options[seed.answer];
      return {
        questionId: question.id,
        question: question.text,
        chosen: chosenOption?.text ?? '미응답',
        correct: correctOption.text,
        explanation: seed.explain,
        isCorrect: chosenOption?.id === correctOption.id,
      };
    });
    const score = items.filter((item) => item.isCorrect).length;
    const result: QuizSubmissionResult = {
      quizId,
      score,
      total: items.length,
      accuracy: Math.round((score / items.length) * 100),
      submittedAt: new Date().toISOString(),
      items,
    };
    saveMockHistory(stored.quiz, result);
    return result;
  }

  const res = await client.post<ApiResponse<ApiSubmissionResultDto> | ApiSubmissionResultDto>(
    `/quizzes/${quizId}/submissions`,
    request
  );
  return toResult(unwrap(res.data));
};

const getHistoryStorageKey = () => 'bt_mock_quiz_history';

const saveMockHistory = (quiz: Quiz, result: QuizSubmissionResult) => {
  const raw = localStorage.getItem(getHistoryStorageKey());
  const history: QuizHistoryItem[] = raw ? JSON.parse(raw) : [];
  const book = MOCK_BOOKS.find((item) => item.id === quiz.bookId);
  const item: QuizHistoryItem = {
    quizId: quiz.id,
    bookId: quiz.bookId,
    bookTitle: book?.title ?? quiz.title,
    score: result.score,
    total: result.total,
    accuracy: result.accuracy,
    submittedAt: result.submittedAt,
  };
  localStorage.setItem(getHistoryStorageKey(), JSON.stringify([item, ...history].slice(0, 20)));
};

export const getMyQuizHistory = async (isMockMode: boolean): Promise<QuizHistoryItem[]> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    const raw = localStorage.getItem(getHistoryStorageKey());
    return raw ? JSON.parse(raw) : [];
  }

  const res = await client.get<ApiResponse<{ content?: QuizHistoryItem[] } | QuizHistoryItem[]>>('/users/me/quizzes');
  const data = unwrap(res.data);
  return Array.isArray(data) ? data : data.content ?? [];
};
