#!/usr/bin/env node
// AI 생성 결과 검수 리포트: 기사 원문(용어 하이라이트) · 요약 · 카드뉴스 · 퀴즈를 근거 문장과 함께 한 화면에 모은다.
// 숫자 근거 검사는 AI 서비스와 같은 코드(ai-service/src/ai.js)를 쓴다. DB는 읽기만 한다.
//
// 사용: node scripts/review-content.mjs [--container youthletter-pipeline-postgres-1] [--db youthletter_pipeline]
//        [--user youthletter] [--limit 50] [--out build/content-review/index.html]
// 결과 HTML에는 기사 본문이 들어가므로 저장소·외부에 올리지 않는다(build/는 Git 제외).
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { sentences, unsupportedNumbers } from '../ai-service/src/ai.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, arg, i, all) =>
  arg.startsWith('--') ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs, []));
const container = args.container ?? 'youthletter-pipeline-postgres-1';
const db = args.db ?? 'youthletter_pipeline';
const user = args.user ?? 'youthletter';
const limit = Number(args.limit ?? 50);
const out = path.resolve(root, args.out ?? 'build/content-review/index.html');
if (!Number.isInteger(limit) || limit < 1) throw new Error('--limit은 1 이상의 정수');

function query(sql) {
  const text = execFileSync('docker', ['exec', '-i', container, 'psql', '-U', user, '-d', db, '-tAq', '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim();
  return text ? JSON.parse(text) : null;
}

// AI 생성물이 하나라도 있는 기사만, 최근 순
const articles = query(`
SELECT coalesce(json_agg(t ORDER BY t.id DESC), '[]') FROM (
  SELECT a.id, a.title, a.content, a.source_url, a.category, a.published_at, a.thumbnail_url,
    (SELECT s.summary_text FROM news_summaries s WHERE s.article_id = a.id AND s.valid_to IS NULL ORDER BY s.id DESC LIMIT 1) AS summary,
    (SELECT coalesce(json_agg(json_build_object('start', h.position_start, 'end', h.position_end, 'name', f.name,
        'simple', f.simple_definition, 'definition', f.definition) ORDER BY h.position_start), '[]')
       FROM article_term_highlights h JOIN financial_terms f ON f.id = h.term_id WHERE h.article_id = a.id) AS terms,
    (SELECT json_build_object('prompt', c.prompt_version, 'model', c.model_version, 'status', c.status,
        'cards', (SELECT coalesce(json_agg(json_build_object('order', k.card_order, 'title', k.title, 'body', k.body,
                   'sub', k.sub_body, 'ids', k.source_sentence_ids) ORDER BY k.card_order), '[]')
                  FROM card_news_cards k WHERE k.card_news_id = c.id))
       FROM card_news c WHERE c.article_id = a.id ORDER BY c.id DESC LIMIT 1) AS card,
    (SELECT coalesce(json_agg(json_build_object('order', z.quiz_order, 'question', z.question, 'choices', z.choices,
        'answer', z.answer_index, 'explanation', z.explanation, 'ids', z.source_sentence_ids, 'prompt', z.prompt_version)
        ORDER BY z.quiz_order), '[]') FROM quizzes z WHERE z.article_id = a.id) AS quizzes,
    (SELECT coalesce(json_agg(json_build_object('kind', j.kind, 'error', j.error_code)), '[]') FROM (
       SELECT DISTINCT ON (kind) kind, status, error_code FROM ai_jobs WHERE article_id = a.id ORDER BY kind, id DESC) j
       WHERE j.status = 'FAILED') AS failed
  FROM news_articles a
  WHERE EXISTS (SELECT 1 FROM news_summaries s WHERE s.article_id = a.id)
     OR EXISTS (SELECT 1 FROM card_news c WHERE c.article_id = a.id)
     OR EXISTS (SELECT 1 FROM quizzes z WHERE z.article_id = a.id)
     OR EXISTS (SELECT 1 FROM ai_jobs j WHERE j.article_id = a.id AND j.status = 'FAILED')
  ORDER BY a.id DESC LIMIT ${limit}) t`);

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function review(a) {
  const source = sentences(a.content);
  const whole = [a.title, a.content].join(' ');
  const issues = [];
  const cite = (ids) => (Array.isArray(ids) ? ids : []).map(id => source[id - 1]
    ? `<li><span class="sid">${id}</span>${esc(source[id - 1].text)}</li>` : `<li class="bad">문장 ${esc(id)} 없음</li>`).join('');
  const evidenceOf = (ids) => [a.title, ...(Array.isArray(ids) ? ids : []).map(id => source[id - 1]?.text ?? '')].join(' ');
  const numbers = (label, text, evidence) => {
    const bad = unsupportedNumbers(text ?? '', evidence);
    if (bad.length) issues.push(`${label}: 근거 없는 숫자 ${bad.join(', ')}`);
    return bad.length ? `<span class="tag bad">근거 없는 숫자 ${esc(bad.join(', '))}</span>` : '<span class="tag ok">숫자 근거 확인</span>';
  };

  // 원문 + 용어 하이라이트(위치가 원문 글자와 맞는지 확인)
  let body = '', cursor = 0;
  for (const t of a.terms) {
    const actual = a.content.slice(t.start, t.end);
    if (actual !== t.name) { issues.push(`용어 위치 불일치: '${t.name}' 위치의 실제 글자 '${actual}'`); continue; }
    if (t.start < cursor) continue;
    body += esc(a.content.slice(cursor, t.start))
      + `<button type="button" class="term" data-name="${esc(t.name)}" data-simple="${esc(t.simple)}" data-definition="${esc(t.definition)}">${esc(actual)}</button>`;
    cursor = t.end;
  }
  body += esc(a.content.slice(cursor));
  if (!a.terms.length) issues.push('용어 하이라이트 없음');

  const summary = a.summary
    ? `<p>${esc(a.summary)}</p>${numbers('요약', a.summary, whole)}`
    : '<p class="muted">요약 없음</p>';
  if (!a.summary) issues.push('요약 없음');

  const cards = a.card?.cards?.length ? a.card.cards.map(c => `
    <div class="item"><div class="item-head"><b>${c.order}. ${esc(c.title)}</b>${numbers(`카드 ${c.order}`, `${c.title} ${c.body} ${c.sub ?? ''}`, evidenceOf(c.ids))}</div>
      <p>${esc(c.body)}</p>${c.sub ? `<p class="muted">${esc(c.sub)}</p>` : ''}
      <details><summary>근거 문장 ${(c.ids ?? []).length}개</summary><ul class="cite">${cite(c.ids)}</ul></details></div>`).join('')
    : '<p class="muted">카드뉴스 없음</p>';
  if (!a.card?.cards?.length) issues.push('카드뉴스 없음');
  else if (a.card.cards.length < 5) issues.push(`카드 ${a.card.cards.length}장(5장 미만)`);

  const quizzes = a.quizzes.length ? a.quizzes.map(z => `
    <div class="item"><div class="item-head"><b>Q${z.order}. ${esc(z.question)}</b>${numbers(`퀴즈 ${z.order} 질문·정답`, `${z.question} ${z.choices[z.answer] ?? ''}`, evidenceOf(z.ids))}</div>
      <ol class="choices">${z.choices.map((c, i) => `<li class="${i === z.answer ? 'answer' : ''}">${esc(c)}${i === z.answer ? ' <span class="tag ok">정답</span>' : ''}</li>`).join('')}</ol>
      <p class="muted">해설: ${esc(z.explanation)} ${numbers(`퀴즈 ${z.order} 해설`, z.explanation, whole)}</p>
      <details><summary>근거 문장 ${(z.ids ?? []).length}개</summary><ul class="cite">${cite(z.ids)}</ul></details></div>`).join('')
    : '<p class="muted">퀴즈 없음</p>';
  if (!a.quizzes.length) issues.push('퀴즈 없음');
  for (const f of a.failed) issues.push(`${f.kind} 생성 실패(${f.error ?? '원인 미상'})`);

  const versions = [a.card?.prompt && `카드 ${a.card.prompt}`, a.quizzes[0]?.prompt && `퀴즈 ${a.quizzes[0].prompt}`].filter(Boolean).join(' · ');
  return { id: a.id, title: a.title, issues, html: `
  <article id="a${a.id}" class="article" data-issues="${issues.length}">
    <header>
      ${a.thumbnail_url ? `<img src="${esc(a.thumbnail_url)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : '<div class="noimg">썸네일 없음</div>'}
      <div><h2>#${a.id} ${esc(a.title)}</h2>
        <p class="muted">${esc(a.category)} · ${esc(a.published_at ?? '발행 시각 없음')} · <a href="${esc(a.source_url)}" target="_blank" rel="noopener noreferrer">원문</a>${versions ? ` · ${esc(versions)}` : ''}</p>
        <label class="done"><input type="checkbox" data-id="${a.id}"> 검수 완료</label></div>
    </header>
    ${issues.length ? `<ul class="issues">${issues.map(i => `<li>${esc(i)}</li>`).join('')}</ul>` : '<p class="tag ok">자동 검사 이상 없음 — 내용(의미)은 직접 확인</p>'}
    <section><h3>요약</h3>${summary}</section>
    <section><h3>카드뉴스</h3>${cards}</section>
    <section><h3>퀴즈</h3>${quizzes}</section>
    <section><h3>원문 <span class="muted">(밑줄 용어를 누르면 뜻)</span></h3><div class="content">${body}</div></section>
  </article>` };
}

const reviewed = articles.map(review);
const withIssues = reviewed.filter(r => r.issues.length).length;
const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI 결과 검수</title>
<style>
:root { --bg:#f6f7f9; --card:#fff; --text:#1d2129; --muted:#6b7280; --line:#e3e6ea; --ok:#0f7b3f; --okbg:#e6f4ec; --bad:#b42318; --badbg:#fdecea; --accent:#2f5bd3; }
@media (prefers-color-scheme: dark) { :root { --bg:#14161a; --card:#1d2026; --text:#e7e9ee; --muted:#9aa1ad; --line:#30343c; --ok:#5fd394; --okbg:#173326; --bad:#ff8a80; --badbg:#3a1d1b; --accent:#8fb0ff; } }
* { box-sizing: border-box; } body { margin:0; background:var(--bg); color:var(--text); font:15px/1.6 -apple-system, "Apple SD Gothic Neo", sans-serif; }
main { max-width: 980px; margin: 0 auto; padding: 16px; } a { color: var(--accent); }
.top { position: sticky; top: 0; background: var(--bg); padding: 12px 0; border-bottom: 1px solid var(--line); z-index: 2; }
.top h1 { margin: 0 0 4px; font-size: 20px; } .toc { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
.toc a { font-size: 13px; padding: 2px 8px; border-radius: 999px; background: var(--card); border: 1px solid var(--line); text-decoration: none; }
.toc a.has { border-color: var(--bad); color: var(--bad); } .toc a.ok { color: var(--ok); }
.article { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 16px; margin: 16px 0; }
header { display: flex; gap: 12px; align-items: flex-start; } header img, .noimg { width: 120px; height: 80px; object-fit: cover; border-radius: 8px; flex: none; background: var(--line); }
.noimg { display:flex; align-items:center; justify-content:center; font-size:12px; color:var(--muted); }
h2 { margin: 0; font-size: 17px; } h3 { font-size: 15px; margin: 16px 0 6px; border-top: 1px solid var(--line); padding-top: 12px; }
.muted { color: var(--muted); font-size: 13px; } .tag { display:inline-block; font-size:12px; padding:1px 8px; border-radius:999px; margin-left:6px; }
.tag.ok { color: var(--ok); background: var(--okbg); } .tag.bad { color: var(--bad); background: var(--badbg); }
.issues { background: var(--badbg); color: var(--bad); border-radius: 8px; padding: 8px 8px 8px 28px; margin: 12px 0 0; font-size: 14px; }
.item { border: 1px solid var(--line); border-radius: 8px; padding: 10px; margin: 8px 0; } .item p { margin: 4px 0; }
.item-head { display:flex; flex-wrap:wrap; align-items:center; gap:4px; } .choices li.answer { font-weight: 600; }
.cite { margin: 6px 0 0; padding-left: 0; list-style: none; font-size: 13px; } .cite li { padding: 4px 0; border-top: 1px dashed var(--line); }
.cite .bad { color: var(--bad); } .sid { display:inline-block; min-width: 22px; color: var(--muted); }
.content { white-space: pre-wrap; font-size: 14px; } .term { font: inherit; color: var(--accent); background: none; border: 0; padding: 0; border-bottom: 2px solid var(--accent); cursor: pointer; }
.done { font-size: 13px; color: var(--muted); } .hidden { display: none; }
#pop { position: fixed; left: 16px; right: 16px; bottom: 16px; max-width: 560px; margin: 0 auto; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 14px; box-shadow: 0 8px 30px rgba(0,0,0,.2); z-index: 5; }
#pop[hidden] { display: none; } #pop button { float: right; }
@media (max-width: 700px) { .top { position: static; } header { flex-direction: column; } header img, .noimg { width: 100%; height: 160px; } }
</style></head><body><main>
<div class="top"><h1>AI 결과 검수 <span class="muted">${esc(db)} · 기사 ${reviewed.length}건 · 자동 검사 문제 ${withIssues}건</span></h1>
<label class="muted"><input type="checkbox" id="onlyIssues"> 문제 있는 기사만</label>
<label class="muted"><input type="checkbox" id="hideDone"> 검수 완료 숨기기</label>
<div class="toc">${reviewed.map(r => `<a href="#a${r.id}" class="${r.issues.length ? 'has' : 'ok'}" title="${esc(r.issues.join(' / ') || '이상 없음')}">#${r.id}${r.issues.length ? ` (${r.issues.length})` : ''}</a>`).join('')}</div></div>
${reviewed.map(r => r.html).join('')}
<div id="pop" hidden role="dialog"><button type="button" id="popClose">닫기</button><b id="popName"></b><p id="popSimple"></p><p class="muted" id="popDef"></p></div>
</main><script>
const store = { get(k) { try { return localStorage.getItem(k); } catch { return null; } }, set(k, v) { try { localStorage.setItem(k, v); } catch {} } };
const pop = document.getElementById('pop');
document.addEventListener('click', e => {
  const t = e.target.closest('.term'); if (!t) return;
  document.getElementById('popName').textContent = t.dataset.name;
  document.getElementById('popSimple').textContent = t.dataset.simple || '(쉬운 뜻 없음)';
  document.getElementById('popDef').textContent = t.dataset.definition && t.dataset.definition !== t.dataset.simple ? t.dataset.definition : '';
  pop.hidden = false;
});
document.getElementById('popClose').onclick = () => { pop.hidden = true; };
function filter() {
  const only = document.getElementById('onlyIssues').checked, hide = document.getElementById('hideDone').checked;
  document.querySelectorAll('.article').forEach(a => {
    const done = a.querySelector('.done input').checked;
    a.classList.toggle('hidden', (only && a.dataset.issues === '0') || (hide && done));
  });
}
document.querySelectorAll('.done input').forEach(box => {
  box.checked = store.get('review-done-' + box.dataset.id) === '1';
  box.onchange = () => { store.set('review-done-' + box.dataset.id, box.checked ? '1' : '0'); filter(); };
});
document.getElementById('onlyIssues').onchange = filter;
document.getElementById('hideDone').onchange = filter;
</script></body></html>`;

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html);
console.log(`검수 리포트: ${out}`);
console.log(`기사 ${reviewed.length}건, 자동 검사 문제 ${withIssues}건`);
for (const r of reviewed.filter(r => r.issues.length).slice(0, 15)) console.log(`  #${r.id} ${r.title.slice(0, 30)} — ${r.issues.join(' / ')}`);
