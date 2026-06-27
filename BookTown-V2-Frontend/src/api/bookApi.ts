import client from './client';

export interface Book {
  id: string;
  title: string;
  author: string;
  genre: string;
  description: string;
  isBookmarked: boolean;
  hasSummary: boolean;
  hasIllust: boolean;
  hasQuiz: boolean;
  chapters: string[];
}

// Mock Data
export const MOCK_BOOKS: Book[] = [
  {
    id: 'pride',
    title: '오만과 편견',
    author: '제인 오스틴',
    genre: '소설',
    description: '베넷 가문의 둘째 딸 엘리자베스와 부유하고 오만한 다아시의 첫 만남에서부터 오해와 편견을 극복하고 진정한 사랑에 이르기까지의 과정을 영국 사회상에 대한 재치 있는 묘사로 그려낸 명작입니다.',
    isBookmarked: false,
    hasSummary: true,
    hasIllust: true,
    hasQuiz: true,
    chapters: ['Chapter 1: 하트퍼드셔의 베넷가', 'Chapter 2: 빙리 씨의 등장', 'Chapter 3: 무도회와 첫 인상', 'Chapter 4: 제인과 엘리자베스의 대화', 'Chapter 5: 루카스 가의 방문'],
  },
  {
    id: 'gatsby',
    title: '위대한 개츠비',
    author: 'F. 스콧 피츠제럴드',
    genre: '소설',
    description: '1920년대 미국 재즈 시대의 화려함 이면의 공허함과, 잃어버린 첫사랑 데이지를 되찾기 위해 엄청난 부를 축적한 신비로운 남자 개츠비의 맹목적인 사랑과 비극적인 삶을 그린 불후의 클래식입니다.',
    isBookmarked: false,
    hasSummary: true,
    hasIllust: true,
    hasQuiz: true,
    chapters: ['Chapter 1: 롱아일랜드의 여름', 'Chapter 2: 재의 계곡과 머틀', 'Chapter 3: 개츠비의 화려한 파티', 'Chapter 4: 차 안에서의 고백', 'Chapter 5: 데이지와의 재회'],
  },
  {
    id: 'romeo',
    title: '로미오와 줄리엣',
    author: '윌리엄 셰익스피어',
    genre: '희곡',
    description: '베로나의 완강한 원수 가문인 몬테규 가의 로미오와 캐퓰릿 가의 줄리엣이 무도회에서 운명적으로 만나 사랑에 빠지지만, 가문 간의 깊은 갈등 속에서 비극적인 죽음을 맞이하는 영원한 사랑의 비극입니다.',
    isBookmarked: false,
    hasSummary: true,
    hasIllust: true,
    hasQuiz: false,
    chapters: ['Act 1: 베로나 광장의 다툼과 무도회', 'Act 2: 발코니의 맹세와 비밀 결혼', 'Act 3: 머큐쇼의 죽음과 로미오의 추방', 'Act 4: 줄리엣의 가사약 복용', 'Act 5: 묘지에서의 비극과 화해'],
  },
  {
    id: 'miserables',
    title: '레 미제라블',
    author: '빅토르 위고',
    genre: '소설',
    description: '빵 한 조각을 훔친 죄로 19년간 감옥살이를 한 장발장이 미리엘 주교의 자비에 감화되어 새로운 삶을 시작하지만, 자베르 경감의 끈질긴 추적 속에서 프랑스 혁명기의 역사 속을 살아가는 대서사시입니다.',
    isBookmarked: false,
    hasSummary: true,
    hasIllust: false,
    hasQuiz: true,
    chapters: ['Part 1: 판틴과 장발장의 구원', 'Part 2: 떼나르디에 부부와 어린 코제트', 'Part 3: 마리우스의 등장과 혁명 모임', 'Part 4: 바리케이드와 혁명의 불꽃', 'Part 5: 하수구 탈출과 최후'],
  },
  {
    id: 'crime',
    title: '죄와 벌',
    author: '표도르 도스토옙스키',
    genre: '소설',
    description: '가난한 대학생 라스콜니코프가 심리적 죄책감과 양심의 가책으로 고통받다가, 헌신적인 소냐의 숭고한 사랑에 이끌려 마침내 죄를 고백하고 시베리아 유형지에서 영혼의 구원을 얻는 과정을 그린 걸작입니다.',
    isBookmarked: false,
    hasSummary: true,
    hasIllust: true,
    hasQuiz: false,
    chapters: ['Part 1: 범행의 계획과 살인', 'Part 2: 열병과 수사관들의 포위망', 'Part 3: 소냐와의 조우와 영혼의 균열', 'Part 4: 나사로의 부활 낭독', 'Part 5: 라스콜니코프의 고백과 자수'],
  },
  {
    id: 'jane',
    title: '제인 에어',
    author: '샬럿 브론테',
    genre: '소설',
    description: '고아로 자라 억압과 고난을 겪으면서도 주체적인 영혼을 잃지 않은 제인 에어가 손필드 저택의 가정교사로 들어가 비밀스러운 주인 로체스터 씨와 격정적 사랑에 빠지는 성장과 열정의 소설입니다.',
    isBookmarked: false,
    hasSummary: false,
    hasIllust: true,
    hasQuiz: true,
    chapters: ['Chapter 1: 게이츠헤드 홀에서의 학대', 'Chapter 2: 로우드 자선학교의 시련', 'Chapter 3: 손필드 저택과 로체스터', 'Chapter 4: 불타는 침대와 비밀의 그림자', 'Chapter 5: 도피와 무어 하우스'],
  },
];

// 로컬 스토리지에 유저의 찜한 상태 보존 (Mock 모드 전용)
const getMockBookmarkedIds = (): string[] => {
  const data = localStorage.getItem('bt_mock_bookmarks');
  return data ? JSON.parse(data) : [];
};

const saveMockBookmarkedIds = (ids: string[]) => {
  localStorage.setItem('bt_mock_bookmarks', JSON.stringify(ids));
};

export const getBooks = async (
  isMockMode: boolean,
  params?: { genre?: string; q?: string }
): Promise<Book[]> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 400));
    const bookmarks = getMockBookmarkedIds();
    let books = MOCK_BOOKS.map((b) => ({
      ...b,
      isBookmarked: bookmarks.includes(b.id),
    }));

    if (params?.genre && params.genre !== '전체') {
      books = books.filter((b) => b.genre === params.genre);
    }
    if (params?.q) {
      const q = params.q.toLowerCase();
      books = books.filter(
        (b) =>
          b.title.toLowerCase().includes(q) ||
          b.author.toLowerCase().includes(q)
      );
    }
    return books;
  }

  // 실제 API 연동
  const res = await client.get('/books', { params });
  return res.data.data;
};

export const getBookById = async (
  isMockMode: boolean,
  bookId: string
): Promise<Book> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    const bookmarks = getMockBookmarkedIds();
    const book = MOCK_BOOKS.find((b) => b.id === bookId);
    if (!book) throw new Error('도서를 찾을 수 없습니다.');
    return {
      ...book,
      isBookmarked: bookmarks.includes(book.id),
    };
  }

  // 실제 API 연동
  const res = await client.get(`/books/${bookId}`);
  return res.data.data;
};

export const toggleBookmarkApi = async (
  isMockMode: boolean,
  bookId: string,
  shouldBookmark: boolean
): Promise<void> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 200));
    const bookmarks = getMockBookmarkedIds();
    if (shouldBookmark) {
      if (!bookmarks.includes(bookId)) {
        saveMockBookmarkedIds([...bookmarks, bookId]);
      }
    } else {
      saveMockBookmarkedIds(bookmarks.filter((id) => id !== bookId));
    }
    return;
  }

  // 실제 API 연동
  if (shouldBookmark) {
    await client.post(`/books/${bookId}/bookmark`);
  } else {
    await client.delete(`/books/${bookId}/bookmark`);
  }
};
