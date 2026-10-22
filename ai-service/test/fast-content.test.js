import test from 'node:test';
import assert from 'node:assert/strict';
import {createAi, sentences, validateQuizzes} from '../src/ai.js';
import {clozeQuizzes, isGenericTerm, termCandidates, isCaptionSentence, rankSentences} from '../src/fast-content.js';
import {selectedCards} from '../src/ai.js';

const content = '은행은 예금과 적금을 취급한다. 대출 금리는 신용도에 따라 달라진다. 금융거래에는 위험이 있다.';
const terms = ['예금','적금','대출','금리','신용도'].map(term=>({term,explanation:`${term}에 관한 쉬운 설명입니다.`}));
const article = {title:'금융 안내',content};

test('한 번의 생성과 한 번의 임베딩으로 용어 5개·카드·퀴즈를 완성한다', async()=>{
  let chats=0,embeds=0;
  const ai=createAi({minimumTerms:5,cardMode:'ranked',quizMode:'cloze',fetchImpl:async(url,init)=>{
    if(url.endsWith('/chat')){
      chats++;
      return {ok:true,json:async()=>({message:{content:JSON.stringify({summary:'금융 상품을 소개한다.',terms})}})};
    }
    embeds++;
    assert.equal(JSON.parse(init.body).truncate,true);
    return {ok:true,json:async()=>({embeddings:[Array(768).fill(0.1)]})};
  }});
  const result=await ai.bundle(article);
  assert.equal(chats,1);assert.equal(embeds,1);
  assert.equal(result.article.terms.length,5);
  assert.equal(result.cards.cards.length,3);
  assert.equal(result.quiz.quizzes.length,3);
  for(const q of result.quiz.quizzes){assert.match(q.question,/원문/);assert.equal(q.choices.length,4);}
});

test('원문에 없는 용어와 중복 용어로 5개를 채운 척하지 않는다',async()=>{
  const ai=createAi({minimumTerms:5,fetchImpl:async()=>({ok:true,json:async()=>({message:{content:JSON.stringify({
    summary:'요약',terms:[terms[0],terms[0],terms[1],terms[2],{term:'없는말',explanation:'설명'}]
  })}})})});
  await assert.rejects(()=>ai.process(article),/INSUFFICIENT_TERMS/);
});

test('사전 설명이 5개 있으면 생성 없이 원문 요약과 결과를 완성한다',async()=>{
  const ai=createAi({minimumTerms:5,summaryMode:'extractive',cardMode:'ranked',quizMode:'cloze',fetchImpl:async(url)=>{
    assert.ok(url.endsWith('/embed'));
    return {ok:true,json:async()=>({embeddings:[Array(768).fill(0.1)]})};
  }});
  const result=await ai.bundle({...article,terms});
  assert.equal(result.article.summary,content);
  assert.equal(result.article.terms.length,5);
  assert.equal(result.quiz.quizzes.length,3);
});

test('빈칸 퀴즈는 숫자를 생성하지 않고 원문의 숫자와 근거를 보존한다',()=>{
  const source=sentences('은행은 2026년 예금과 적금을 안내했다. 대출 금리는 3.5%이며 신용도에 따라 달라진다. 금융거래에는 위험이 있다.');
  const quizzes=validateQuizzes(clozeQuizzes(source,terms),source);
  assert.equal(quizzes.length,3);
  assert.equal(clozeQuizzes(source,[{term:'없는말'}]),null);
});

test('모델이 용어를 반복하면 고른 용어를 뺀 후보로 한 번 더 요청해 5개를 채운다',async()=>{
  const replies=[[terms[0],terms[0],terms[1]],[terms[2],terms[3],terms[4]]];
  const asked=[];
  const ai=createAi({minimumTerms:5,summaryMode:'extractive',fetchImpl:async(url,init)=>{
    if(url.endsWith('/embed')) return {ok:true,json:async()=>({embeddings:[Array(768).fill(0.1)]})};
    asked.push(JSON.parse(init.body).format.properties.terms.items.properties.term.enum);
    return {ok:true,json:async()=>({message:{content:JSON.stringify({terms:replies[asked.length-1]})}})};
  }});
  const result=await ai.process(article);
  assert.equal(asked.length,2);
  assert.ok(!asked[1].includes('예금')&&!asked[1].includes('적금'));
  assert.deepEqual(result.terms.map(t=>t.term),['예금','적금','대출','금리','신용도']);
});

test('일반 단어·금액 단위·조사 붙은 형태·잘린 단어·회사명은 용어로 쓰지 않는다',()=>{
  const text='정부 예산이 늘었다. 예산을 줄인다. 네이버페이는 결제 수단이다. 네이버페이가 1조원 기준을 넘었다. 신한카드와 저축은행 대출 금리가 올랐다.';
  for(const t of ['기준','발생','한국','억원','억달러','예산이','네이버페','신한카드','하나금융그룹']) assert.ok(isGenericTerm(t,text),t);
  for(const t of ['예산','네이버페이','저축은행','금리','대출','가업상속공제','AI']) assert.ok(!isGenericTerm(t,text),t);
  const candidates=termCandidates(text);
  for(const t of ['기준','예산이','네이버페','신한카드']) assert.ok(!candidates.includes(t),t);
});

test('사전 용어가 일부만 있으면 그 용어를 쓰고 모자란 개수만 생성한다',async()=>{
  const asked=[];
  const ai=createAi({minimumTerms:5,summaryMode:'extractive',fetchImpl:async(url,init)=>{
    if(url.endsWith('/embed')) return {ok:true,json:async()=>({embeddings:[Array(768).fill(0.1)]})};
    asked.push(JSON.parse(init.body).format.properties.terms.items.properties.term.enum);
    return {ok:true,json:async()=>({message:{content:JSON.stringify({terms:terms.slice(2)})}})};
  }});
  const result=await ai.process({...article,terms:[terms[0],terms[1],{term:'기준',explanation:'일반 단어'}]});
  assert.equal(asked.length,1);
  assert.ok(!asked[0].includes('예금')&&!asked[0].includes('적금'));
  assert.deepEqual(result.terms.map(t=>t.term),['예금','적금','대출','금리','신용도']);
});

test('용어 생성이 출력 한도로 잘리면 한 번 더 요청한다',async()=>{
  let chats=0;
  const ai=createAi({minimumTerms:5,summaryMode:'extractive',fetchImpl:async(url)=>{
    if(url.endsWith('/embed')) return {ok:true,json:async()=>({embeddings:[Array(768).fill(0.1)]})};
    chats++;
    return {ok:true,json:async()=>chats===1?{done_reason:'length',message:{content:'{}'}}:{message:{content:JSON.stringify({terms})}}};
  }});
  assert.equal((await ai.process(article)).terms.length,5);
  assert.equal(chats,2);
});

test('조사가 붙은 형태는 떼고, ○○페이는 그대로 둔다',()=>{
  const text='강소기업이 늘었다. 인프라가 부족하다. 카카오페이는 결제 수단이다.';
  const c=termCandidates(text);
  assert.ok(c.includes('강소기업')&&!c.includes('강소기업이'));
  assert.ok(c.includes('카카오페이'));
  assert.ok(!c.includes('인프라가'));
  for(const t of ['강소기업이','예산이','인프라가']) assert.ok(isGenericTerm(t,text),t);
  for(const t of ['물가','국제유가','전문가','강소기업','카카오페이']) assert.ok(!isGenericTerm(t,text),t);
});

test('인명·용언 활용형·복수형은 용어로 쓰지 않는다',()=>{
  const text='박창훈 사장은 취업중인 한국인들과 이주한 사람들이 대우받아야 한다고 말했다. 신한카드 대출 금리.';
  for(const t of ['박창훈','취업중인','한국인들','이주한','대우받아야']) assert.ok(isGenericTerm(t,text),t);
  for(const t of ['금리','대출','가업상속공제']) assert.ok(!isGenericTerm(t,text),t);
});

test('빈칸은 단어 중간을 자르지 않고, 문제마다 다른 문장과 다른 정답 위치를 쓴다',()=>{
  const ts=['공제','자산','투자','소득','세금'].map(term=>({term,explanation:`${term} 설명`}));
  const source=sentences('사진=chat gpt 내년 가상자산 투자로 돈을 벌었다. 연간 기본공제 250만원을 뺀다. 공제 한도는 정해져 있다. 자산 관리가 중요하다. 소득에 세금이 붙는다.');
  const {quizzes}=clozeQuizzes(source,ts);
  assert.equal(quizzes.length,3);
  for(const q of quizzes){
    assert.ok(!/사진=/.test(q.question),q.question);
    assert.ok(!/기본□|가상□/.test(q.question),q.question);
  }
  assert.equal(new Set(quizzes.map(q=>q.sourceSentenceIds[0])).size,3);
  assert.equal(new Set(quizzes.map(q=>q.answerIndex)).size,3);
  validateQuizzes({quizzes},source);
});

test('캡션·기자 이메일 문장은 카드와 요약에 쓰지 않는다',()=>{
  assert.ok(isCaptionSentence('사진=뉴스1 정부가 석유 가격을 동결한다.'));
  assert.ok(isCaptionSentence('정영효 기자 hugh@hankyung.com'));
  assert.ok(!isCaptionSentence('정부가 석유 가격을 동결한다.'));
  const source=sentences('사진=뉴스1 정부가 발표했다. 금리가 올랐다. 대출이 줄었다. 홍길동 기자 a@hankyung.com');
  const cards=selectedCards(rankSentences(source,[]),source);
  assert.deepEqual(cards.map(c=>c.sourceSentenceIds[0]),[2,3]);
});

test('두 글자 조사와 하다·되다가 붙은 용어도 빈칸으로 쓴다',async()=>{
  const {wholeWordAt}=await import('../src/fast-content.js');
  const ok=(text,term)=>wholeWordAt(text,term,text.indexOf(term));
  assert.ok(ok('금융감독원에는 신고가 없다.','금융감독원'));
  assert.ok(ok('페이지가 해킹돼 고객 정보가 샜다.','해킹'));
  assert.ok(ok('금리가 올랐다.','금리'));
  assert.ok(!ok('대출신청 웹사이트가 열렸다.','대출'));
  assert.ok(!ok('OK저축은행 고객이 늘었다.','저축은행'));
  assert.ok(!ok('연간 기본공제를 뺀다.','공제'));
});

test('학습 용어가 합성어 안에만 있으면 원문 후보 단어로 남은 빈칸을 채운다',()=>{
  const ts=['대출','저축은행','은행','해킹','연소득'].map(term=>({term,explanation:'설명'}));
  const source=sentences('OK저축은행 대출신청 페이지가 해킹돼 고객 정보가 샜다. 고객 연소득 정보도 포함됐다. 금융감독원에는 신고가 없었다. 회사는 보안 점검을 시작했다.');
  assert.equal(clozeQuizzes(source,ts),null);
  const {quizzes}=clozeQuizzes(source,ts,['금융감독원','보안','점검','고객']);
  assert.equal(quizzes.length,3);
  assert.deepEqual(quizzes.slice(0,2).map(q=>q.choices[q.answerIndex]),['해킹','연소득']);
  validateQuizzes({quizzes},source);
});

test('빈칸·뜻 고르기·용어 고르기를 섞어 3문제를 만든다',async()=>{
  const {mixedQuizzes,termQuizzes}=await import('../src/fast-content.js');
  const ts=[['예금','은행에 돈을 맡기는 것'],['적금','매달 정해진 돈을 넣는 저축'],['대출','돈을 빌리는 것'],
    ['금리','돈을 빌리거나 맡길 때 붙는 이자의 비율'],['신용도','빚을 갚을 능력에 대한 평가']].map(([term,explanation])=>({term,explanation}));
  const source=sentences('은행은 예금과 적금을 취급한다. 대출 금리는 신용도에 따라 달라진다. 금융거래에는 위험이 있다.');
  const {quizzes}=mixedQuizzes(source,ts,[]);
  assert.equal(quizzes.length,3);
  assert.match(quizzes[0].question,/빈칸/);
  assert.match(quizzes[1].question,/뜻으로 알맞은 것은/);
  assert.match(quizzes[2].question,/설명에 해당하는/);
  assert.equal(quizzes[1].choices[quizzes[1].answerIndex],'은행에 돈을 맡기는 것');
  validateQuizzes({quizzes},source);
  // 숫자가 든 설명이나 용어 이름이 드러나는 설명은 쓰지 않는다
  assert.deepEqual(termQuizzes(source,[{term:'예금',explanation:'예금은 1년 단위'},...ts.slice(1,4)]),[]);
});

test('같은 개념의 다른 이름(AI·인공지능)은 뜻 고르기 보기에 함께 나오지 않는다',async()=>{
  const {termQuizzes}=await import('../src/fast-content.js');
  const ts=[['인공지능','컴퓨터가 사람처럼 학습하고 판단하도록 만든 기술이에요.'],['AI','인공지능. 컴퓨터가 사람처럼 학습하고 판단하도록 만든 기술이에요.'],
    ['예산','정부나 기관이 특정 목적에 사용하는 돈'],['이자','돈을 빌린 대가로 내는 비용'],['국가채무','정부가 갚아야 할 빚']].map(([term,explanation])=>({term,explanation}));
  const source=sentences('올해 인공지능(AI) 호황으로 세수가 늘었다. 예산과 이자 부담이 커졌다. 국가채무 관리가 중요하다.');
  const quizzes=termQuizzes(source,ts);
  assert.equal(quizzes.length,2);
  for(const q of quizzes){
    const all=q.choices.join('|');
    assert.ok(!(all.includes('인공지능')&&all.includes('AI'))||!/뜻으로/.test(q.question),all);
    assert.ok(q.choices.filter(c=>c.includes('컴퓨터가 사람처럼')).length<=1,all);
  }
});

test('제목 끝 숫자와 근거 문장 첫 숫자를 한 숫자로 합치지 않는다(…51조 + 18일…)',async()=>{
  const {unsupportedNumbers}=await import('../src/ai.js');
  const title='나랏빚 여유 부렸다간… 4년뒤 이자만 年 51조';
  const source=sentences('올해 세수가 늘었다. 18일 국회 자료에 따르면 국고채 이자는 34조4220억원이다. 이자 부담이 커졌다.');
  const cards=selectedCards({sentenceIds:[1,2,3]},source,title);
  assert.equal(cards.length,3);
  // 같은 줄의 띄어쓰기는 여전히 한 숫자로 읽는다
  assert.deepEqual(unsupportedNumbers('14만 9576명','14만9576명이 참여했다'),[]);
});

test('활용형·부사·그룹 회사명은 용어로 쓰지 않는다(2026-10-22 점검)',()=>{
  const text='삼성전자와 한화솔루션, 우리금융이 지금 가장 주목받는다. 비용이 얼마나 드는지도 걸리는지 모른다. 증권가에선 인수한 회사가 증발했으니 제도인데 연장되지 않는다. 우리사주 제도와 기준금리, 가업상속공제, 피상속인을 본다.';
  for(const t of ['삼성전자','한화솔루션','우리금융','지금','가장','얼마나','걸리는지','드는지도','증권가에선','인수한','증발했으니','제도인데','연장되지','LG','김구'])
    assert.ok(isGenericTerm(t,text),t);
  for(const t of ['우리사주','기준금리','가업상속공제','피상속인','금리','대출','저축은행','국제유가'])
    assert.ok(!isGenericTerm(t,text),t);
});
