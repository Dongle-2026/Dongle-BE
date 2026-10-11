# 카드뉴스 자동 생성 기능 기획 및 구현 문서

> **작성일:** 2026-05-18  
> **작성자:** 정다은  
> **서비스명:** 돈글돈글 (유스레터)  
> **기능명:** 경제 뉴스 카드뉴스 자동 생성

---

## 1. 기능 개요

경제 뉴스 기사 원문을 **카드뉴스 형태의 학습 콘텐츠**로 변환하는 기능이다.

단순 요약 이미지 생성이 아니라, 사용자가 **핵심 내용 파악 → 개념 보완 → 맥락 이해 → 학습 점검** 흐름으로 접근할 수 있도록 설계된 학습 지원 기능이다.

사전에 정의된 카드 템플릿 세트를 기반으로 기사 내용을 자동 배치하는 방식으로 구현하여 결과물의 일관성, 가독성, 구현 가능성을 확보한다.

---

## 2. 기획 배경

경제 뉴스는 청년이 자주 접하는 정보이지만, 기사 표현이 어렵고 맥락이 복잡해 실제 이해로 이어지지 않는 경우가 많다. 핵심 이슈, 수치 변화, 배경 설명, 금융 용어가 혼합되어 있어 초심자에게 진입 장벽이 높다.

기존 뉴스 서비스는 기사 원문 제공에 집중하며, 학습 흐름까지 고려된 구조는 부족하다. 돈글돈글은 경제 뉴스를 **학습 가능한 카드형 콘텐츠**로 재구성함으로써 청년 사용자가 부담 없이 접근하고 이해할 수 있도록 돕고자 한다.

---

## 3. 기능 목표

1. 경제 뉴스의 핵심 내용을 짧고 읽기 쉬운 카드 단위로 분해한다.
2. 기사 내 복잡한 개념과 수치를 청년 눈높이에 맞게 재구성한다.
3. 카드 디자인의 일관성과 가독성을 유지한다.
4. 기사별 카드뉴스를 자동 또는 반자동으로 생성할 수 있는 구조를 만든다.
5. 이후 용어 설명, 원문 보기, 퀴즈 기능과 자연스럽게 연결되도록 한다.

---

## 4. 차별점

| 구분 | 중심 |
|------|------|
| 일반 카드뉴스 제작 툴 | 디자인 중심 |
| 일반 요약 기능 | 텍스트 축약 중심 |
| **돈글돈글 카드뉴스** | **기사 이해 흐름 설계 + 개념 보완 + 사용자 영향 설명 + 학습 점검 연결** |

---

## 5. 생성 흐름

```
[입력] 기사 정보
  ↓
[전처리] 본문 정리 / 핵심 문단 추출 / 수치·키워드·용어 후보 식별
  ↓
[카드 콘텐츠 생성] 카드 수 결정 / 카드별 핵심 문장 생성 / 카드 유형 분류 / 순서 결정
  ↓
[템플릿 선택] 카드 유형 → 템플릿 자동 매핑 / 텍스트 길이 기준 세부 템플릿 선택
  ↓
[렌더링] 템플릿에 텍스트 삽입 / 강조 요소 적용 / 카드 이미지 출력
  ↓
[후처리] 카드 순서 검토 / 원문·용어·퀴즈 기능 연결
```

### 5.1 입력 데이터

| 필드 | 설명 |
|------|------|
| `title` | 기사 제목 |
| `content` | 기사 본문 |
| `category` | 기사 카테고리 |
| `publishedAt` | 기사 발행일 |
| `source` | 출처 정보 |

---

## 6. 카드 구성 원칙

1. 한 카드에는 핵심 내용 1개만 담는다.
2. 긴 문장보다 짧은 설명 위주로 구성한다.
3. 수치, 변화, 원인, 의미를 분리해서 보여준다.
4. 첫 장부터 마지막 장까지 사용자가 자연스러운 이해 흐름을 따라가도록 설계한다.
5. 마지막 카드는 원문 보기 또는 퀴즈와 연결될 수 있도록 한다.

---

## 7. 카드 템플릿 구성 (10종)

### T01. 표지형 (Cover)
- **용도:** 카드뉴스 첫 장
- **구성:** 기사 제목 + 한 줄 요약
- **목적:** 기사 주제와 핵심 이슈 제시

### T02. 핵심 요약형 (Summary)
- **용도:** 전체 기사 핵심 요약
- **구성:** 핵심 문장 1~2개
- **목적:** 본문 전에 전체 메시지 파악

### T03. 개념 설명형 (Concept)
- **용도:** 어려운 개념 설명
- **구성:** 개념명 + 쉬운 설명
- **목적:** 금융 용어와 기사 개념 이해 보완

### T04. 수치 강조형 (Stat)
- **용도:** 금리, 물가, 증가율 등 수치 정보 설명
- **구성:** 핵심 수치 + 의미 해설
- **목적:** 숫자 정보의 직관적 이해

### T05. 비교형 (Compare)
- **용도:** 이전/이후, A/B 비교
- **구성:** 항목 2개 + 차이 설명
- **목적:** 변화와 차이점 이해

### T06. 원인 설명형 (Cause)
- **용도:** 왜 이런 결과가 나왔는지 설명
- **구성:** 현상 + 원인
- **목적:** 배경 이해

### T07. 영향 설명형 (Impact)
- **용도:** 뉴스가 사용자에게 어떤 의미인지 설명
- **구성:** 핵심 변화 + 청년 관점 영향
- **목적:** 정보와 사용자 연결

### T08. 맥락 흐름형 (Context)
- **용도:** 시간 흐름이나 이슈 배경 설명
- **구성:** 이전 상황 + 현재 변화
- **목적:** 기사 맥락 이해

### T09. 용어 정리형 (Glossary)
- **용도:** 기사 내 핵심 용어 정리
- **구성:** 용어 + 정의 + 쉬운 표현
- **목적:** 본문 이해 보조

### T10. 마무리/퀴즈 연결형 (Outro)
- **용도:** 마지막 카드
- **구성:** 요약 문장 + 다음 행동 유도
- **목적:** 원문 보기, 퀴즈 풀기, 추가 학습 연결

---

## 8. 카드 유형 자동 매핑 규칙

| 조건 | 적용 템플릿 |
|------|------------|
| 첫 장 | T01 표지형 |
| 기사 전체 핵심 | T02 핵심 요약형 |
| 어려운 개념 포함 | T03 개념 설명형 |
| 수치·지표 포함 | T04 수치 강조형 |
| 두 항목 비교 필요 | T05 비교형 |
| 이유 설명 필요 | T06 원인 설명형 |
| 사용자 영향 강조 | T07 영향 설명형 |
| 전후 맥락 설명 필요 | T08 맥락 흐름형 |
| 핵심 용어 정리 필요 | T09 용어 정리형 |
| 마지막 장 | T10 마무리/퀴즈 연결형 |

---

## 9. 텍스트 제한 규칙

| 요소 | 글자 수 제한 |
|------|------------|
| 카드 제목 | 18자 이내 |
| 핵심 문장 | 35자 이내 |
| 보조 설명 | 45자 이내 |
| 용어 설명 | 30자 이내 |

**초과 시 처리 방식:**
1. 문장 재요약
2. 핵심 키워드만 추출
3. 카드 분할

---

## 10. 생성 방식 (단계별)

### 1단계: 반자동 방식 (현재 구현 목표)
- AI가 기사 요약 및 카드 문구 초안 생성
- 사람이 1차 검토 후 저장
- 템플릿에 자동 삽입

### 2단계: 규칙 기반 자동 생성 (향후 목표)
- 기사 특징 분석 → 카드 수 자동 결정
- 템플릿 자동 선택 → 이미지 자동 렌더링

> 졸업작품 단계에서는 **반자동 기반 + 일부 자동화** 구현을 목표로 한다.

---

## 11. DB 스키마

### V12 — card_news, card_news_cards

```sql
CREATE TABLE card_news (
    id          BIGSERIAL PRIMARY KEY,
    news_id     BIGINT REFERENCES news(id),
    total_cards INT NOT NULL DEFAULT 0,
    status      VARCHAR(20) DEFAULT 'DRAFT',   -- DRAFT, PUBLISHED
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE card_news_cards (
    id              BIGSERIAL PRIMARY KEY,
    card_news_id    BIGINT REFERENCES card_news(id) ON DELETE CASCADE,
    card_order      INT NOT NULL,               -- 카드 순서 (1-based)
    template_type   VARCHAR(20) NOT NULL,       -- T01~T10
    title           VARCHAR(50),               -- 카드 제목 (18자 이내)
    body            VARCHAR(200),              -- 핵심 문장 (35자 이내)
    sub_body        VARCHAR(200),              -- 보조 설명 (45자 이내)
    highlight       VARCHAR(100),              -- 강조 요소 (수치, 용어명 등)
    image_url       TEXT,                      -- 렌더링된 카드 이미지 URL (nullable)
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_card_news_news ON card_news(news_id);
CREATE INDEX idx_card_news_cards_order ON card_news_cards(card_news_id, card_order);
```

---

## 12. 패키지 구조

```
src/main/java/com/youthletter/
└── cardnews/
    ├── controller/   CardNewsController.java
    ├── service/      CardNewsService.java, CardNewsGenerationService.java
    ├── repository/   CardNewsRepository.java, CardNewsCardRepository.java
    ├── dto/
    │   ├── CardNewsResponse.java
    │   ├── CardNewsDetailResponse.java
    │   └── CardNewsCardResponse.java
    └── entity/
        ├── CardNews.java
        └── CardNewsCard.java
```

---

## 13. API 엔드포인트

| 기능 ID | 기능명 | 메서드 | 엔드포인트 | 인증 | 상태 |
|---------|--------|--------|-----------|------|------|
| CN-01 | 카드뉴스 생성 요청 | POST | `/api/card-news/{newsId}/generate` | 필요 (ADMIN) | 🔶 stub |
| CN-02 | 카드뉴스 조회 | GET | `/api/card-news/{newsId}` | 불필요 | ✅ |
| CN-03 | 카드뉴스 단일 카드 조회 | GET | `/api/card-news/{newsId}/cards/{order}` | 불필요 | ✅ |
| CN-04 | 카드뉴스 목록 조회 | GET | `/api/card-news?page=&size=` | 불필요 | ✅ |

### 요청/응답 예시

**CN-01: 카드뉴스 생성 요청**
```json
// POST /api/card-news/42/generate
// Response 200
{
  "success": true,
  "message": "카드뉴스 생성이 완료되었습니다.",
  "data": {
    "cardNewsId": 1,
    "newsId": 42,
    "totalCards": 7,
    "status": "PUBLISHED"
  }
}
```

**CN-02: 카드뉴스 조회**
```json
// GET /api/card-news/42
// Response 200
{
  "success": true,
  "message": "success",
  "data": {
    "cardNewsId": 1,
    "newsId": 42,
    "newsTitle": "한국은행, 기준금리 0.25%p 인하",
    "totalCards": 7,
    "cards": [
      {
        "cardOrder": 1,
        "templateType": "T01",
        "title": "기준금리 인하",
        "body": "한국은행이 기준금리를 0.25%p 낮췄습니다.",
        "subBody": null,
        "highlight": "0.25%p 인하",
        "imageUrl": null
      }
    ]
  }
}
```

---

## 14. 엔티티 설계

### CardNews.java
```java
@Entity @Table(name = "card_news")
@Getter @NoArgsConstructor
public class CardNews {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id")
    private News news;

    private int totalCards;

    @Enumerated(EnumType.STRING)
    private CardNewsStatus status;    // DRAFT, PUBLISHED

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### CardNewsCard.java
```java
@Entity @Table(name = "card_news_cards")
@Getter @NoArgsConstructor
public class CardNewsCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id")
    private CardNews cardNews;

    private int cardOrder;

    @Enumerated(EnumType.STRING)
    private CardTemplateType templateType;  // T01~T10

    private String title;        // 18자 이내
    private String body;         // 35자 이내
    private String subBody;      // 45자 이내
    private String highlight;
    private String imageUrl;     // nullable

    private LocalDateTime createdAt;
}
```

---

## 15. AI 연동 (CardNewsGenerationService)

```java
@Service
@RequiredArgsConstructor
public class CardNewsGenerationService {

    private final AiServiceClient aiServiceClient;
    private final CardNewsRepository cardNewsRepository;
    private final CardNewsCardRepository cardNewsCardRepository;

    @Value("${ai.service.stub-mode:true}")
    private boolean stubMode;

    @Transactional
    public CardNews generate(News news) {
        List<CardNewsCardDto> cards;

        if (stubMode) {
            cards = generateStubCards(news);  // mock 카드 데이터 반환
        } else {
            cards = aiServiceClient.generateCardNews(news.getId());
        }

        CardNews cardNews = saveCardNews(news, cards);
        return cardNews;
    }

    private List<CardNewsCardDto> generateStubCards(News news) {
        // 템플릿 T01~T10 기반 mock 카드 7장 생성
        return List.of(
            new CardNewsCardDto(1, "T01", news.getTitle().substring(0, Math.min(18, news.getTitle().length())), "핵심 내용 한 줄 요약입니다.", null, null),
            new CardNewsCardDto(2, "T02", "핵심 요약", "이 기사의 핵심 내용입니다.", "부가 설명을 여기에 작성합니다.", null)
            // ... 나머지 카드
        );
    }
}
```

> AI 서비스 연동 완료 후 `stub-mode: false`로 전환하고 실제 Node.js 호출로 교체한다.

---

## 16. AI 서비스 연동 스펙 (Node.js ↔ Spring Boot)

### 요청 (Spring Boot → Node.js)
```
POST http://localhost:3000/card-news/generate
Content-Type: application/json

{
  "newsId": 42,
  "title": "기사 제목",
  "content": "기사 본문 전문",
  "category": "ECONOMY"
}
```

### 응답 (Node.js → Spring Boot)
```json
{
  "cards": [
    {
      "order": 1,
      "templateType": "T01",
      "title": "카드 제목 (18자 이내)",
      "body": "핵심 문장 (35자 이내)",
      "subBody": "보조 설명 (45자 이내)",
      "highlight": "강조 키워드"
    }
  ]
}
```

---

## 17. 향후 확장 방향

1. 기사 카테고리별 템플릿 최적화
2. 사용자 이해도 기반 카드 난이도 조절
3. 용어 강조 카드 자동 삽입 (terms 테이블 연동)
4. 카드뉴스 이후 퀴즈 자동 연결
5. 사용자 저장/즐겨찾기 기반 복습 카드 추천
6. 카드 이미지 렌더링 서버 분리 (템플릿 → PNG 변환)

---

## 18. 개발 상태 체크리스트

| 항목 | 상태 | 비고 |
|------|------|------|
| DB 스키마 설계 | ✅ 완료 | V12 마이그레이션 |
| 엔티티 구현 | ⬜ 미완 | CardNews, CardNewsCard |
| Repository 구현 | ⬜ 미완 | |
| Service 구현 (stub) | ⬜ 미완 | stub 모드 우선 |
| Controller 구현 | ⬜ 미완 | CN-01~CN-04 |
| AI 서비스 연동 | ⬜ 대기 | Node.js 완성 후 |
| 이미지 렌더링 | ⬜ 대기 | 향후 확장 |
