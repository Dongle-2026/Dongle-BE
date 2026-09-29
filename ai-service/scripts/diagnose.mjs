import { createAi } from '../src/ai.js';
const ai = createAi({ observe: event => console.log(JSON.stringify(event)) });
try {
  const result = await ai.process({ title: '테스트', content: '저축은 사용하지 않은 돈을 보관하는 행동입니다.' });
  console.log(JSON.stringify({ status: 'OK', embeddingDimensions: result.embedding.length, termCount: result.terms.length }));
} catch (error) {
  console.error(JSON.stringify({ status: 'FAILED', code: error.code ?? 'UNKNOWN' }));
  process.exitCode = 1;
}
