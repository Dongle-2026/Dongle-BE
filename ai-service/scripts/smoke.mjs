import assert from 'node:assert/strict';
const base = process.env.AI_SERVICE_BASE_URL ?? 'http://127.0.0.1:3000';
const article = { title: '청년 금융 학습 안내', category: 'YOUTH', articleUrl: 'https://example.invalid/fixture',
  content: '이 문서는 실제 뉴스가 아닌 연동 시험용 자료입니다. 청년 금융 교육 과정은 예산과 저축의 의미를 설명합니다. 예산은 일정 기간 사용할 수 있는 금액을 미리 정하는 계획입니다. 저축은 소득 중 사용하지 않은 금액을 보관하는 행동입니다. 교육 과정에서는 소비 내역을 기록하고 주기적으로 확인하도록 안내합니다. 참여자는 자신의 생활에 맞는 지출 계획을 작성할 수 있습니다.' };
async function request(path, body) {
  const start = Date.now();
  const response = await fetch(`${base}${path}`, { method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', 'X-AI-Service-Token': process.env.AI_SERVICE_TOKEN ?? '' },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(250000) });
  const result = await response.json();
  assert.equal(response.status, 200, JSON.stringify(result));
  console.log(path, 'PASS', `${Date.now() - start}ms`);
  return result;
}
await request('/health');
const processResult = await request('/api/ai/process', article);
assert.equal(processResult.embedding.length, 768);
assert.ok(processResult.summary);
console.log('summary:', processResult.summary, 'terms:', processResult.terms.length, 'embedding:', processResult.embedding.length);
const cards = await request('/api/ai/card-news', article);
assert.ok(cards.cards.length >= 5);
console.log('cards:', cards.cards.length, 'model:', cards.modelVersion);
const insight = await request('/api/ai/spending-insight', { month: '2026-09', total: 10000, previousTotal: 8000, categoryTotals: { FOOD: 10000 } });
assert.ok(!/\d/.test(insight.advice));
console.log('advice:', insight.advice);
