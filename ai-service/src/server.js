import express from 'express';
import { pathToFileURL } from 'node:url';
import { timingSafeEqual, randomUUID } from 'node:crypto';
import { createAi, AiError } from './ai.js';

export function createApp(ai = createAi({ observe: event => console.info(JSON.stringify(event)) }), token = process.env.AI_SERVICE_TOKEN ?? '', log = event => console.info(JSON.stringify(event))) {
  const app = express();
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    const supplied = req.get('X-Request-ID') ?? '';
    const requestId = /^[a-f0-9-]{36}$/.test(supplied) ? supplied : randomUUID();
    const started = performance.now();
    res.set('X-Request-ID', requestId);
    res.on('finish', () => {
      try { log({ requestId, route: req.route?.path ?? 'UNMATCHED', status: res.statusCode,
        code: res.locals.errorCode ?? 'OK', durationMs: Math.round(performance.now() - started) }); } catch { /* 관측 실패 분리 */ }
    });
    next();
  });
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
  for (const [path, method] of [['process', 'process'], ['bundle', 'bundle'], ['card-news', 'cards'], ['spending-insight', 'insight'], ['quiz', 'quiz']]) {
    app.post(`/api/ai/${path}`, async (req, res) => {
      if (busy) { res.locals.errorCode = 'AI_BUSY'; return res.status(429).json({ error: 'AI_BUSY' }); }
      busy = true;
      try { res.json(await ai[method](req.body)); } finally { busy = false; }
    });
  }
  app.use((err, req, res, next) => {
    const status = err instanceof AiError ? err.status : err.status === 400 || err.status === 413 ? err.status : 500;
    res.locals.errorCode = err instanceof AiError ? err.code : 'INVALID_REQUEST_OR_INTERNAL_ERROR';
    res.status(status).json({ error: res.locals.errorCode });
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
