import test from 'node:test';
import assert from 'node:assert/strict';
import { createAi, validateCards, validateQuizzes, screenQuizzes, checkSelfSolved, unsupportedNumbers, numberValue, supportSentenceIds, retryHint, sentences, articleInput } from '../src/ai.js';

const article = { title: '금리 안내', content: '기준금리는 3%입니다. 청년 지원을 안내합니다.' };
const source = sentences(article.content);
test('JSON 스키마와 제한된 출력량을 모델에 전달한다', async () => {
  const ai = createAi({ fetchImpl: async (url, init) => {
    if (url.endsWith('/chat')) {
      const request = JSON.parse(init.body);
      assert.equal(request.format.type, 'object');
      assert.equal(request.format.properties.summary.maxLength, undefined);
      assert.equal(request.format.properties.terms.type, 'array');
      assert.deepEqual(request.format.required, ['summary', 'terms']);
      assert.equal(request.think, false);
      assert.equal(request.options.num_predict, 1000);
      return { ok: true, json: async () => ({ message: { content: '{"summary":"요약","terms":[]}' } }) };
    }
    return { ok: true, json: async () => ({ embeddings: [Array(768).fill(0.01)] }) };
  } });
  assert.equal((await ai.process(article)).summary, '요약');
});
test('출력 토큰 한도에 도달한 결과는 저장 후보에서 제외한다', async () => {
  const ai = createAi({ fetchImpl: async () => ({ok:true,json:async()=>({
    done_reason:'length',message:{content:'{"summary":"요약","terms":[]}'}
  })}) });
  await assert.rejects(()=>ai.process(article), /MODEL_OUTPUT_TRUNCATED/);
});
test('모델 타임아웃은 단계와 코드만 기록한다', async () => {
  const events = [];
  const ai = createAi({ observe: event => events.push(event), fetchImpl: async () => {
    throw new DOMException('sensitive upstream text', 'TimeoutError');
  } });
  await assert.rejects(() => ai.process(article), /OLLAMA_TIMEOUT/);
  assert.equal(events[0].stage, '/api/chat');
  assert.equal(events[0].code, 'OLLAMA_TIMEOUT');
  assert.equal(events[0].status, 0);
  assert.equal(JSON.stringify(events).includes('sensitive'), false);
  assert.equal(JSON.stringify(events).includes(article.content), false);
});
test('관측 처리 실패는 정상 모델 응답을 실패시키지 않는다', async () => {
  const ai = createAi({ observe: () => { throw new Error('logger'); },
    fetchImpl: async () => ({ ok: true, json: async () => ({ models: [] }) }) });
  assert.equal((await ai.health()).status, 'MISSING_MODELS');
});
const cards = () => ({ cards: Array.from({ length: 5 }, () => ({ templateType: 'T01', title: '금리 안내', body: '기준금리는 3%입니다.', sourceSentenceIds: [1] })) });
test('기사 입력 공백과 과도한 길이를 거부한다', () => {
  assert.throws(() => articleInput({ title: 'a', content: ' ' }));
  assert.throws(() => articleInput({ title: 'a', content: 'a'.repeat(16001) }));
});
test('카드 순서와 근거를 정규화한다', () => {
  assert.equal(validateCards(cards(), source)[4].cardOrder, 5);
});
test('근거 없는 숫자·근거 ID·과도한 글자를 거부한다', () => {
  const wrong = cards(); wrong.cards[0].body = '금리는 99%입니다.';
  assert.throws(() => validateCards(wrong, source), /UNSUPPORTED_NUMBER/);
  wrong.cards[0].sourceSentenceIds = [99];
  assert.throws(() => validateCards(wrong, source), /INVALID_CARDS/);
});
test('NER 위치는 모델이 아닌 원문에서 계산하며 임베딩 계약을 확인한다', async () => {
  const ai = createAi({ fetchImpl: async url => ({ ok: true, json: async () => url.endsWith('/chat')
    ? { message: { content: JSON.stringify({ summary: '요약입니다.', terms: [{ term: '기준금리', explanation: '기준이 되는 금리' }] }) } }
    : { embeddings: [Array(768).fill(0.01)] } }) });
  const result = await ai.process(article);
  assert.equal(result.terms[0].startIdx, 0);
  assert.equal(result.terms[0].endIdx, 4);
  assert.equal(result.embedding.length, 768);
});
test('1536차원 결과를 허용하지 않는다', async () => {
  const ai = createAi({ fetchImpl: async url => ({ ok: true, json: async () => url.endsWith('/chat')
    ? { message: { content: '{"summary":"요약","terms":[]}' } } : { embeddings: [Array(1536).fill(0.1)] } }) });
  await assert.rejects(() => ai.process(article), /INVALID_EMBEDDING/);
});
test('카드 검증 실패 시 한 번만 재생성한다', async () => {
  let calls = 0;
  const ai = createAi({ fetchImpl: async () => {
    calls++; return { ok: true, json: async () => ({ message: { content: JSON.stringify(calls === 1 ? { cards: [] } : cards()) } }) };
  } });
  assert.equal((await ai.cards(article)).cards.length, 5);
  assert.equal(calls, 2);
});
test('소비 제안에 모델이 숫자를 넣으면 거부한다', async () => {
  const ai = createAi({ fetchImpl: async () => ({ ok: true, json: async () => ({ message: { content: '{"advice":"15% 절약하세요"}' } }) }) });
  await assert.rejects(() => ai.insight({ month: '2026-09', total: 1000 }), /INVALID_ADVICE/);
});
test('모델 미설치와 연결 장애를 성공으로 보고하지 않는다', async () => {
  const ai = createAi({ fetchImpl: async () => ({ ok: true, json: async () => ({ models: [] }) }) });
  assert.equal((await ai.health()).status, 'MISSING_MODELS');
  await assert.rejects(() => createAi({ fetchImpl: async () => { throw new Error(); } }).health(), /OLLAMA_UNAVAILABLE/);
});

const quizArticle = { title: '청년도약계좌', content: '청년도약계좌는 5년 만기 적금이다. 정부기여금은 최대 월 2.4만원이다. 가입 대상은 만 19~34세 청년이다.' };
const quizSource = sentences(quizArticle.content);
const goodQuiz = (overrides = {}) => ({ question: '청년도약계좌의 만기는?', choices: ['1년', '3년', '5년', '10년'], answerIndex: 2,
  explanation: '기사에서 5년 만기라고 설명한다.', sourceSentenceIds: [1], ...overrides });
test('퀴즈 3문제를 근거 문장과 함께 검증해 반환한다', async () => {
  let requested, calls = 0;
  const ai = createAi({ fetchImpl: async (url, init) => {
    calls++;
    if (calls === 2) return { ok: true, json: async () => ({ message: { content: '{"answers":[2,1,0]}' } }) }; // 자기 풀이 일치
    requested = JSON.parse(init.body);
    return { ok: true, json: async () => ({ message: { content: JSON.stringify({ quizzes: [goodQuiz(),
      goodQuiz({ question: '정부기여금 최대 금액은?', choices: ['월 1만원', '월 2.4만원', '월 5만원', '없음'], answerIndex: 1, explanation: '최대 월 2.4만원이다.', sourceSentenceIds: [2] }),
      goodQuiz({ question: '가입 대상 나이는?', choices: ['만 19~34세', '만 40세 이상', '제한 없음', '만 15세 이하'], answerIndex: 0, explanation: '만 19~34세 청년이 대상이다.', sourceSentenceIds: [3, 3] })] }) } }) };
  } });
  const result = await ai.quiz(quizArticle);
  assert.equal(result.promptVersion, 'quiz-v6-pool-evidence');
  assert.equal(calls, 2);
  assert.equal(result.quizzes.length, 3);
  assert.deepEqual(result.quizzes[2].sourceSentenceIds, [3]);
  assert.equal(requested.format.properties.quizzes.items.properties.choices.minItems, undefined);
  assert.equal(requested.options.num_predict, 1520); // 3문제 + 예비 1문제
  assert.equal(requested.format.properties.quizzes.type, 'array');
});
test('퀴즈 검증: 개수·중복 보기·정답 범위·근거 없는 숫자를 거부한다', () => {
  const three = (q) => ({ quizzes: [q, goodQuiz({ question: 'b' }), goodQuiz({ question: 'c' })] });
  assert.throws(() => validateQuizzes({ quizzes: [goodQuiz()] }, quizSource), /INVALID_QUIZ/);
  assert.throws(() => validateQuizzes(three(goodQuiz({ choices: ['1년', '1년', '5년', '10년'] })), quizSource), /INVALID_QUIZ/);
  assert.throws(() => validateQuizzes(three(goodQuiz({ answerIndex: 4 })), quizSource), /INVALID_QUIZ/);
  assert.throws(() => validateQuizzes(three(goodQuiz({ sourceSentenceIds: [9] })), quizSource), /INVALID_QUIZ/);
  assert.throws(() => validateQuizzes(three(goodQuiz({ explanation: '7년 만기다.' })), quizSource), /UNSUPPORTED_NUMBER/);
  assert.throws(() => validateQuizzes({ quizzes: [goodQuiz(), goodQuiz(), goodQuiz({ question: 'c' })] }, quizSource), /INVALID_QUIZ/);
  // 오답 보기의 숫자(1년, 3년, 10년)는 근거가 없어도 된다
  assert.equal(validateQuizzes(three(goodQuiz()), quizSource)[0].answerIndex, 2);
});
test('퀴즈 첫 출력이 검증 실패하면 한 번만 재시도한다', async () => {
  let calls = 0;
  const ai = createAi({ fetchImpl: async () => { calls++; return { ok: true, json: async () => ({ message: { content: '{"quizzes":[]}' } }) }; } });
  await assert.rejects(() => ai.quiz(quizArticle), /INVALID_QUIZ/);
  assert.equal(calls, 2);
});

test('자기 풀이 정답이 다르면 다시 출제하고, 또 다르면 저장하지 않는다', async () => {
  const three = JSON.stringify({ quizzes: [goodQuiz(), goodQuiz({ question: 'b', explanation: '기사에서 설명한다.' }), goodQuiz({ question: 'c', explanation: '기사에서 설명한다.' })] });
  let calls = 0;
  const ai = createAi({ fetchImpl: async () => {
    calls++;
    const content = calls % 2 === 1 ? three : '{"answers":[2,2,0]}'; // 3번째 문제 불일치
    return { ok: true, json: async () => ({ message: { content } }) };
  } });
  await assert.rejects(() => ai.quiz(quizArticle), /QUIZ_SELF_CHECK_FAILED/);
  assert.equal(calls, 4); // 출제+풀이 2회
  assert.throws(() => checkSelfSolved([goodQuiz()], { answers: [] }), /QUIZ_SELF_CHECK_FAILED/);
  assert.throws(() => checkSelfSolved([goodQuiz()], { answers: [-1] }), /QUIZ_SELF_CHECK_FAILED/);
  assert.equal(checkSelfSolved([goodQuiz()], { answers: [2] }).length, 1);
});

test('숫자 근거: 단위까지 같은 덩어리여야 하고 잘린·지어낸 숫자는 거부한다', () => {
  assert.deepEqual(unsupportedNumbers('새 기준선은 519만원', '약 519만원이 새 기준선이 된다.'), []);
  assert.deepEqual(unsupportedNumbers('6월 17일 시행', '오는 6월17일부터 시행된다.'), []);
  assert.deepEqual(unsupportedNumbers('1,490.6원', '1490.6원에 마쳤다'), []);
  assert.deepEqual(unsupportedNumbers('2025년 6월 17일', '오는 6월17일부터 시행된다.'), ['2025']);
  assert.deepEqual(unsupportedNumbers('평균 21만 원', '평균 21만9000 원을 추가'), ['21만']);
  assert.deepEqual(unsupportedNumbers('17년 만에', '2017년 이후'), ['17']);
});
test('재시도 때 거부된 숫자를 구체적으로 알려준다', async () => {
  const bad = JSON.stringify({ quizzes: [goodQuiz({ explanation: '2025년 5년 만기다.' }), goodQuiz({ question: 'b' }), goodQuiz({ question: 'c' })] });
  const prompts = [];
  const ai = createAi({ fetchImpl: async (url, init) => {
    const body = JSON.parse(init.body);
    if (body.format.properties.answers) {
      const n = JSON.parse(body.messages[1].content.split('입력: ')[1]).quizzes.length;
      return { ok: true, json: async () => ({ message: { content: JSON.stringify({ answers: Array(n).fill(2) }) } }) };
    }
    prompts.push(body.messages[1].content);
    return { ok: true, json: async () => ({ message: { content: bad } }) };
  } });
  // b·c는 채택, 1번은 두 번 모두 거부 → 3문제를 못 채우면 저장하지 않는다
  await assert.rejects(() => ai.quiz(quizArticle), /UNSUPPORTED_NUMBER/);
  assert.equal(prompts.length, 2);
  assert.match(prompts[1], /1번 문제의 숫자 2025는 근거 문장에 없다/);
  assert.match(prompts[1], /이미 채택된 질문과 겹치지 않게/);
  assert.match(retryHint({ code: 'QUIZ_SELF_CHECK_FAILED' }, '문제'), /정답을 하나로 고를 수 없었다/);
});

test('한 문제가 거부돼도 나머지는 채택하고, 부족한 만큼만 다시 출제한다', async () => {
  const events = [];
  const batches = [
    { quizzes: [goodQuiz({ explanation: '2025년 5년 만기다.' }), goodQuiz({ question: 'b' }), goodQuiz({ question: 'c' }), goodQuiz({ question: 'd' })] },
    { quizzes: [goodQuiz({ question: 'b' }), goodQuiz({ question: 'e' })] },
  ];
  const asked = [];
  let solveCalls = 0;
  const ai = createAi({ observe: e => events.push(e), fetchImpl: async (url, init) => {
    const body = JSON.parse(init.body);
    if (body.format.properties.answers) {
      solveCalls++;
      // 첫 묶음: b 통과, c 통과, d 자기 풀이 불일치 / 둘째 묶음: e 통과
      const answers = solveCalls === 1 ? [2, 2, 0] : [2];
      return { ok: true, json: async () => ({ message: { content: JSON.stringify({ answers }) } }) };
    }
    asked.push(Number(body.messages[1].content.match(/정확히 (\d)문제/)[1]));
    return { ok: true, json: async () => ({ message: { content: JSON.stringify(batches[asked.length - 1]) } }) };
  } });
  const result = await ai.quiz(quizArticle);
  assert.deepEqual(result.quizzes.map(q => q.question), ['b', 'c', 'e']);
  assert.deepEqual(asked, [4, 2]);
  const rejected = events.filter(e => e.stage === 'quiz-item-rejected');
  assert.deepEqual(rejected.find(e => e.code === 'UNSUPPORTED_NUMBER').numbers, ['2025']);
  assert.ok(rejected.some(e => e.code === 'QUIZ_SELF_CHECK_FAILED'));
});

test('후보 검증: 형식 오류·중복·이미 채택된 질문을 걸러낸다', () => {
  const { valid, errors } = screenQuizzes({ quizzes: [goodQuiz(), goodQuiz(), goodQuiz({ answerIndex: 7 }), goodQuiz({ question: 'x' })] },
    quizSource, quizArticle.title, [{ question: 'x' }]);
  assert.equal(valid.length, 1);
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'INVALID_QUIZ');
  assert.deepEqual(screenQuizzes(null, quizSource).valid, []);
});

test('숫자는 표기가 달라도 같은 값이면 인정한다', () => {
  assert.equal(numberValue('1천만'), numberValue('1,000만'));
  assert.equal(numberValue('1천만'), numberValue('10000000'));
  assert.equal(numberValue('3조5천억'), '3500000000000');
  assert.equal(numberValue('50퍼센트'), '50%');
  assert.notEqual(numberValue('50'), numberValue('50%'));
  assert.deepEqual(unsupportedNumbers('1천만원 지원', '지원금은 1천만원이다.'), []);
  assert.deepEqual(unsupportedNumbers('1,000만원 지원', '지원금은 1천만원이다.'), []);
  assert.deepEqual(unsupportedNumbers('3조5천억원 규모', '총 3조5천억원 규모다.'), []);
  assert.deepEqual(unsupportedNumbers('50% 증가', '50퍼센트 늘었다.'), []);
  assert.deepEqual(unsupportedNumbers('5천만원', '지원금은 1천만원이다.'), ['5천만']);
  assert.deepEqual(unsupportedNumbers('50만원', '50% 늘었다.'), ['50만']);
});

test('근거 문장 자동 추가: 기사 속 같은 값의 문장 중 인용 문장에서 가장 가까운 것', () => {
  const src = sentences('A는 1만3700건이다. B는 늘었다. C도 늘었다. D는 1만3700건이 아니다. 2023년부터 시행했다.');
  assert.deepEqual(supportSentenceIds('1만3700건', [3], src), [3, 4]); // 1번(거리 2)보다 4번(거리 1)
  assert.deepEqual(supportSentenceIds('1만3700건, 2023년부터', [2], src), [2, 1, 5]);
  assert.deepEqual(supportSentenceIds('9999건', [2], src), [2]); // 기사에 없음 → 추가 없음
  assert.deepEqual(supportSentenceIds('1만3700건', [1, 1], src), [1]); // 이미 근거 있음
  const many = sentences('1이다. 2이다. 3이다. 4이다. 5이다. 없다.');
  assert.equal(supportSentenceIds('1 2 3 4 5', [6], many).length, 4); // 최대 3개 추가
  const card = cards(); card.cards[0].body = '청년 지원'; card.cards[1].body = '기준금리 3%'; card.cards[1].sourceSentenceIds = [2];
  assert.deepEqual(validateCards(card, source)[1].sourceSentenceIds, [2, 1]);
});

test('요약에 기사에 없는 숫자가 있으면 한 번 다시 요약하고, 또 있으면 저장하지 않는다', async () => {
  const prompts = [];
  const summaries = ['인구의 30%가 약자다.', '기준금리는 3%다.'];
  const ai = createAi({ fetchImpl: async (url, init) => {
    if (url.endsWith('/embed')) return { ok: true, json: async () => ({ embeddings: [Array(768).fill(0.01)] }) };
    prompts.push(JSON.parse(init.body).messages[1].content);
    return { ok: true, json: async () => ({ message: { content: JSON.stringify({ summary: summaries[prompts.length - 1], terms: [] }) } }) };
  } });
  assert.equal((await ai.process(article)).summary, '기준금리는 3%다.');
  assert.match(prompts[1], /이전 요약의 숫자 30%는 기사에 없다/);
  const always = createAi({ fetchImpl: async (url) => url.endsWith('/embed')
    ? { ok: true, json: async () => ({ embeddings: [Array(768).fill(0.01)] }) }
    : { ok: true, json: async () => ({ message: { content: '{"summary":"인구의 30%","terms":[]}' } }) } });
  await assert.rejects(() => always.process(article), /UNSUPPORTED_NUMBER/);
});

test('"N가지"처럼 문제 구조를 말하는 작은 개수는 검사하지 않는다', () => {
  assert.deepEqual(unsupportedNumbers('기사가 든 2가지 이유는?', '금리와 환율이 원인이다.'), []);
  assert.deepEqual(unsupportedNumbers('2개 은행', '금리와 환율이 원인이다.'), ['2']);
  assert.deepEqual(unsupportedNumbers('30가지 상품', '상품을 출시했다.'), ['30']);
});

test('제목의 숫자도 질문·정답·카드의 근거로 인정한다', () => {
  const article = { title: '청년 1만명 지원', content: '정부가 청년 지원을 늘린다. 신청은 다음 달부터다.' };
  const src = sentences(article.content);
  const q = goodQuiz({ question: '몇 명을 지원하나?', choices: ['1천명', '5천명', '1만명', '10만명'], answerIndex: 2, explanation: '청년 지원을 늘린다.', sourceSentenceIds: [1] });
  assert.equal(screenQuizzes({ quizzes: [q] }, src, article.title).valid.length, 1);
  assert.equal(screenQuizzes({ quizzes: [q] }, src, '').errors[0].code, 'UNSUPPORTED_NUMBER');
});

test('해설의 숫자는 기사 안에만 있으면 되고, 질문·정답의 숫자는 근거 문장에 있어야 한다', () => {
  const three = (q) => ({ quizzes: [q, goodQuiz({ question: 'b' }), goodQuiz({ question: 'c' })] });
  // 해설이 다른 문장(2번)의 숫자를 인용 → 허용
  assert.equal(validateQuizzes(three(goodQuiz({ explanation: '5년 만기이며 정부기여금은 최대 월 2.4만원이다.' })), quizSource)[0].answerIndex, 2);
  // 해설에 기사에 없는 숫자 → 거부
  assert.throws(() => validateQuizzes(three(goodQuiz({ explanation: '2025년부터 5년 만기다.' })), quizSource), /UNSUPPORTED_NUMBER/);
  // 정답 보기의 숫자가 인용 문장(1번)에 없고 다른 문장(2번)에만 있음 → 2번 문장을 근거에 추가
  assert.deepEqual(validateQuizzes(three(goodQuiz({ choices: ['1년', '3년', '2.4만원', '10년'] })), quizSource)[0].sourceSentenceIds, [1, 2]);
  // 기사 어디에도 없는 숫자 → 거부
  assert.throws(() => validateQuizzes(three(goodQuiz({ choices: ['1년', '3년', '7만원', '10년'] })), quizSource), /UNSUPPORTED_NUMBER/);
});

test('100분의 N과 N%는 같은 값으로 인정한다', () => {
  assert.deepEqual(unsupportedNumbers('당월 보험료의 100% 이상', '당월 보험료의 100분의 100 이상인 경우'), []);
  assert.deepEqual(unsupportedNumbers('30% 이상', '100분의 30 이상'), []);
  assert.deepEqual(unsupportedNumbers('50% 이상', '100분의 30 이상'), ['50%']);
});

test('행사명 숫자만으로 달력 연도를 만들지 않는다', () => {
  assert.deepEqual(unsupportedNumbers('2026년 22일 시상', '오는 22일 2026 한국광고주대회를 연다.'), ['2026년']);
  assert.deepEqual(unsupportedNumbers('2026년 시상', '2026 년에 시상한다.'), []);
  assert.deepEqual(unsupportedNumbers('2026 광고주대회', '2026 한국광고주대회'), []);
  assert.deepEqual(unsupportedNumbers('2026년', '12026년'), ['2026']);
});
