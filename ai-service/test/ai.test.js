import test from 'node:test';
import assert from 'node:assert/strict';
import { createAi, validateCards, sentences, articleInput } from '../src/ai.js';

const article = { title: '금리 안내', content: '기준금리는 3%입니다. 청년 지원을 안내합니다.' };
const source = sentences(article.content);
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
