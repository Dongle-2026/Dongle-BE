import express from 'express';
import { pathToFileURL } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { createAi, AiError } from './ai.js';

export function createApp(ai = createAi(), token = process.env.AI_SERVICE_TOKEN ?? '') {
  const app = express();
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    if (token) {
      const actual = Buffer.from(req.get('X-AI-Service-Token') ?? '');
      const expected = Buffer.from(token);
      if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) return res.status(401).json({ error: 'UNAUTHORIZED' });
    }
    next();
  });
  app.use(express.json({ limit: '100kb' }));
  app.get('/health', async (req, res) => {
    const result = await ai.health();
    res.status(result.status === 'UP' ? 200 : 503).json(result);
  });
  // 로컬 모델 과부하 방지: 대기열을 무한히 쌓지 않는다.
  let busy = false;
  for (const [path, method] of [['process', 'process'], ['card-news', 'cards'], ['spending-insight', 'insight']]) {
    app.post(`/api/ai/${path}`, async (req, res) => {
      if (busy) return res.status(429).json({ error: 'AI_BUSY' });
      busy = true;
      try { res.json(await ai[method](req.body)); } finally { busy = false; }
    });
  }
  app.use((err, req, res, next) => {
    const status = err instanceof AiError ? err.status : err.status === 400 || err.status === 413 ? err.status : 500;
    res.status(status).json({ error: err instanceof AiError ? err.code : 'INVALID_REQUEST_OR_INTERNAL_ERROR' });
  });
  return app;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host = process.env.HOST ?? '127.0.0.1';
  if (!['127.0.0.1', 'localhost', '::1'].includes(host) && !process.env.AI_SERVICE_TOKEN) {
    throw new Error('AI_SERVICE_TOKEN is required when binding outside localhost');
  }
  createApp().listen(Number(process.env.PORT ?? 3000), host, (error) => {
    if (error) { console.error('AI Service listen failed:', error.code); process.exitCode = 1; }
    else console.log('AI Service ready');
  });
}
