import { createAi } from '../src/ai.js';
const ai = createAi({ fetchImpl: async (url, options) => {
  const result = await fetch(url, options);
  console.log(new URL(url).pathname, result.status);
  if (!result.ok) console.log(await result.clone().text());
  return result;
} });
await ai.process({ title: '테스트', content: '저축은 사용하지 않은 돈을 보관하는 행동입니다.' });
