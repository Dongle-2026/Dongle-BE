// 원문에 실제 존재하는 표현으로만 만드는 빠른 발췌 카드와 원문 빈칸 퀴즈.
// 경제·금융 개념에 자주 쓰이는 형태소. 후보 순위를 정할 때만 사용하고 원문에 없는 용어는 만들지 않는다.
const FINANCE = /금리|대출|예금|적금|채권|주식|증시|펀드|보험|연금|세금|과세|공제|상속|증여|환율|물가|인플레|금융|자산|부채|채무|신용|담보|상환|이자|배당|투자|수익|손실|적자|흑자|무역|수출|수입|관세|예산|재정|기금|보증|보조금|지원금|청약|전세|월세|임대|부동산|주택|분양|가계|소득|소비자|경기침체|성장률|지수|코스피|코스닥|원화|달러|가상자산|코인|핀테크|결제|카드|은행|증권|저축|통화|기준금리|유동성|레버리지|리스크|규제|인가|상장|공모|합병|인수|지분|매출|영업이익|순이익|실적|고용률|실업|최저임금|임금|퇴직|서민|중소기업|자영업|소상공인|ETF|ISA|IRP|DSR|LTV|DTI|GDP|CPI|IPO|ESG|AI/u;
const STOP = new Set(['이번','지난','전날','이날','다양한','위해','통해','따라','대해','대한','관련','지난해','올해','내년',
  '기사','기자','신문','뉴스','사진','제공','오늘','내일','최근','현재','이후','이전','이상','이하','가운데','경우','당시','이후',
  '전국','국내','해외','서울','부산','인천','대구','광주','대전','울산','세종','경기도','지역','사람','우리','이들','그는','그러나',
  '하지만','또한','특히','한편','이어','등을','등의','것으로','것이','있다','없다','했다','한다','밝혔다','말했다','설명했다',
  '전했다','대상','진행','구성','작업','이달','행사','분야','부담','필요','약','총','더','또','및','등','중','위','첫','각','JOB','The','and',
  // 실제 실행에서 용어로 잘못 뽑힌 일반 단어·고유명사
  '기준','발생','확정','한국','미국','일본','중국','네덜란드','싱가포르','인구','정책','일자리','기관','경영','경력','이력서',
  '컨설턴트','아동','농산물','사업장','대표이사','위원장','선거','대화','규모','특별','사회적','지역사회','지속가능한','조직',
  '구조','기술','과제','부품','인력','교수','법원','관행적','비현실적','무단결근','포용력','정체성','이민자','차림','스펙',
  '서기관','경기','제도','청년','국가','정부','시장','기업','회사','계획','방안','상황','문제','사업','활동','서비스','프로그램',
  '하나','송도','재단','배송','사회','차별','취임','번째','대표','시행','분류','비용','장애인','외국인','인프라','방식','효과',
  '영향','발표','추진','개최','운영','지원','확대','강화','개선','도입','마련','협력','참여','성과','변화','수준','증가','감소',
  '상승','하락','전망','예정','예상','가능성','방향','목표','판매','생산','이용','사용','관리','개발','연구','조사','결과',
  '내용','의견','사업자','직원','고객','소비자','국민','주민','시민','학생','노동자','근로자','회장','사장','장관','의원',
  '대통령','총리','중심','처리','절차','재정경제부','재경부','행시','반대','추가','불만','사건','출마','차기','협약식','조정관','업무시간','사과','판정','승진','CCTV','KB','지역희망나눔재단','전국푸드뱅크','발데싱','NCDR','박창훈','경찰대학','시간','구글','타임라인','기록','위치','데이터','자료','정보','수단','방법','날짜','기획','논의','검토','협의','합의','발언','주장','지적','강조','요구','요청','제안','공개','출시','선정']);
// 금액 단위·조사가 붙은 형태는 용어가 아니다.
const UNIT = /^(?:[0-9,.]*)(?:억|조|만|천)?(?:원|달러|억원|억달러|조원|만원|명|건|개|곳|배|퍼센트)$/u;
const PARTICLE = /[이가을를은는의에와과도로]$/u;
// '가'로 끝나는 실제 명사(물가·유가·전문가 등). 그 밖의 '…가'는 주격 조사로 본다.
const GA_NOUN = /(?:물|유|주|원|단|시|호|고|저|지|평|국|대|작|휴|허|인|부|감|전문|투자|사업|자본|은행|혁신|기업|창업|정치|예술)가$/u;
const isParticleTail = w => /[이을를은는]$/u.test(w) ? !w.endsWith('페이') : w.endsWith('가') && !GA_NOUN.test(w);
// 원문에서 stem 뒤에 tail이 아닌 글자가 오는 경우가 있으면, stem+tail은 단어+조사다.
const usedWithoutTail = (stem, tail, content) => {
  for (let at = content.indexOf(stem); at >= 0; at = content.indexOf(stem, at + 1))
    if (content[at + stem.length] !== tail) return true;
  return false;
};
const COMPANY = /(?:금융그룹|금융지주|증권|카드|은행|보험|캐피탈|그룹|재단|공사|협회|센터|부|청|처|원|위원회)$/u;
const GENERIC_FINANCE = new Set(['증권','카드','은행','보험','공사']);

/** 학습용 용어로 쓰면 안 되는 일반 단어·단위·조사 붙은 형태·기관명인지 판단한다. */
export function isGenericTerm(term, content = '') {
  if (typeof term !== 'string') return true;
  const t = term.trim();
  if (t.length < 2 || STOP.has(t) || UNIT.test(t)) return true;
  // '예산이'처럼 조사가 붙었고 조사를 뗀 형태가 단어로 쓰이면 거부한다.
  if (PARTICLE.test(t) && t.length >= 3 && (isParticleTail(t) || usedWithoutTail(t.slice(0, -1), t.at(-1), content))) return true;
  // '네이버페(이)'처럼 원문에서 항상 더 긴 단어의 앞부분으로만 나오면 잘린 조각이다.
  if (t.endsWith('페') && content.includes(t + '이')) return true;
  // 용언 활용형·복수형('취업중인·이주한·대우받아야·한국인들')은 용어가 아니다.
  if (t.length >= 3 && /(?:중인|[가-힣]한|아야|어야|들)$/u.test(t) && !FINANCE.test(t)) return true;
  // 직함 앞의 세 글자 이름은 인명이다('박창훈 사장').
  if (/^[가-힣]{3}$/u.test(t) && new RegExp(`${t}\\s?(?:사장|대표|회장|위원장|장관|의원|교수|씨|기자|원장|총재|부회장|이사장|행장|국장|실장|청장)`, 'u').test(content)) return true;
  // 회사·기관 고유명은 용어가 아니다. 단 '저축은행·인터넷전문은행'처럼 금융 개념인 경우는 둔다.
  if (COMPANY.test(t) && !GENERIC_FINANCE.has(t) && !/저축은행|전문은행|중앙은행|보험료|공사채/.test(t) && /^[가-힣]{2,4}(?:금융그룹|금융지주|증권|카드|그룹|재단)$/u.test(t)) return true;
  return false;
}

export function termCandidates(content, limit = 40) {
  const words = content.match(/[가-힣]{2,20}|[A-Za-z][A-Za-z0-9-]{1,20}/g) ?? [];
  const strip = word => {
    const w = word.replace(/(?:으로부터|에게서|에서는|이라는|이라고|에게|에서|으로|에는|부터|까지|보다|처럼|라는|이나|와의|과의|하는|한다|했다|됐다|하며|하고|하기|이다|이며|이라|이란|은|는|을|를|와|과|의|에)$/u, '');
    // 한 글자 조사(이·가·도·만·로)는 '물가·주가'처럼 단어 끝일 수 있어 근거가 있을 때만 뗀다.
    const short = w.replace(/[이가도만로란을를은는]$/u, '');
    if (short === w || short.length < 2) return w;
    // 이·을·를·은·는은 명사 끝 글자로 거의 쓰이지 않아 항상 뗀다('○○페이'는 예외). 가·도·로·만은 원문 근거가 있을 때만 뗀다.
    if (isParticleTail(w)) return short;
    return usedWithoutTail(short, w.at(-1), content) ? short : w;
  };
  const counts = new Map();
  for (const word of words) {
    const w = strip(word);
    if (isGenericTerm(w, content) || !content.includes(w)) continue;
    if (/(?:다|다고|라며|지만|해서|으며|면서|있는|없는|했던|되는|하게|에서|적인|스럽|했고|하지|하기|임에|으로|르고|되고|하고|이고|지고)$/u.test(w)) continue;
    if (/^[A-Za-z]/.test(w) && !/^[A-Z0-9-]{2,}$/.test(w) && !FINANCE.test(w)) continue; // 영문은 약어만
    counts.set(w, (counts.get(w) ?? 0) + 1);
  }
  const score = w => counts.get(w) + (FINANCE.test(w) ? 5 : 0) + (w.length >= 4 ? 1 : 0);
  return [...counts.keys()].sort((a, b) => score(b) - score(a) || content.indexOf(a) - content.indexOf(b)).slice(0, limit);
}

export function rankSentences(source, terms = []) {
  const candidates = source.filter(s => s.text.length <= 200);
  const first = candidates.find(s => s.id === 1);
  if (!first || candidates.length < 2) return null;
  const score = s => terms.reduce((sum, t) => sum + (s.text.includes(t.term) ? 1 : 0), 0);
  return { sentenceIds: [first, ...candidates.filter(s => s.id !== 1)
    .sort((a, b) => score(b) - score(a) || a.id - b.id)].slice(0, 5).map(s => s.id) };
}

export function clozeQuizzes(source, terms = []) {
  const names = [...new Set(terms.map(t => t.term).filter(t => typeof t === 'string'
    && t.length >= 2 && t.length <= 40 && source.some(s => s.text.includes(t))))];
  if (names.length < 5) return null;
  const quizzes = [];
  for (const name of names) {
    for (const sentence of source) {
      const at = sentence.text.indexOf(name);
      if (at < 0) continue;
      if (/[\d,.]/.test(sentence.text[at - 1] ?? '')) continue;
      // 양쪽을 공백 경계에서 자른다. 날짜·금액의 중간을 잘라 새 숫자를 만들지 않는다.
      let start = Math.max(0, at - 25);
      if (start > 0) { const space = sentence.text.indexOf(' ', start); start = space >= 0 && space < at ? space + 1 : 0; }
      let end = Math.min(sentence.text.length, at + name.length + 35);
      if (end < sentence.text.length) { const space = sentence.text.lastIndexOf(' ', end); end = space > at + name.length ? space : sentence.text.length; }
      const quote = sentence.text.slice(start, at) + '□' + sentence.text.slice(at + name.length, end);
      const question = `기사 원문의 빈칸에 들어갈 표현은?\n“${start ? '…' : ''}${quote}${end < sentence.text.length ? '…' : ''}”`;
      if (question.length > 120) continue;
      const choices = [name, ...names.filter(n => n !== name).slice(0, 3)];
      if (choices.length !== 4) continue;
      const explanation = `원문의 표현은 ‘${name}’입니다. 해당 문장을 다시 확인하세요.`;
      if (!quizzes.some(q => q.question === question)) quizzes.push({question, choices, answerIndex:0,
        explanation, sourceSentenceIds:[sentence.id]});
      break;
    }
    if (quizzes.length === 3) break;
  }
  return quizzes.length === 3 ? {quizzes} : null;
}
