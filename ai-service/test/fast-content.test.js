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
