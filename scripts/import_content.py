#!/usr/bin/env python3
"""파이프라인 DB의 완성된 콘텐츠(기사·요약·용어·카드뉴스·퀴즈)를 다른 DB로 옮긴다.

- 완성된 기사만 옮긴다: 현재 요약, 카드뉴스, 퀴즈 3문항, 용어 5개 이상을 모두 갖춘 기사.
- 대상 DB에 같은 원문 URL의 기사가 이미 있으면 건너뛴다(기존 기사·사용자 기록을 바꾸지 않는다).
- 용어 사전은 같은 이름이 있으면 대상의 것을 유지하고, 없는 용어만 추가한다.
- 대상 DB에서는 하나의 트랜잭션으로 실행한다. --dry-run은 끝에서 ROLLBACK한다.

두 DB 모두 Docker 컨테이너 안의 psql로 접속한다(비밀번호를 명령줄에 쓰지 않는다).

예) 파이프라인 DB → 운영 Compose DB
  python3 scripts/import_content.py --target-container youthletter-postgres-prod \\
      --target-db youthletter --target-user youthletter --dry-run
"""
import argparse
import subprocess
import sys

COMPLETE = """
    a.id IN (SELECT s.article_id FROM news_summaries s WHERE s.valid_to IS NULL)
    AND a.id IN (SELECT c.article_id FROM card_news c JOIN card_news_cards x ON x.card_news_id = c.id)
    AND a.id IN (SELECT q.article_id FROM quizzes q GROUP BY q.article_id HAVING count(*) = 3)
    AND a.id IN (SELECT h.article_id FROM article_term_highlights h GROUP BY h.article_id HAVING count(DISTINCT h.term_id) >= 5)
"""

# (임시 테이블 정의, 원본에서 뽑을 SELECT). 텍스트 COPY 형식이라 줄바꿈이 한 줄로 이스케이프된다.
TABLES = [
    ("s_articles(id bigint, source_url text, source_name text, title text, content text, category text, "
     "published_at timestamptz, crawled_at timestamptz, embedding text, thumbnail_url text)",
     f"SELECT a.id, a.source_url, a.source_name, a.title, a.content, a.category, a.published_at, a.crawled_at, "
     f"a.embedding::text, a.thumbnail_url FROM news_articles a WHERE {COMPLETE}"),
    ("s_summaries(article_id bigint, summary_text text, model_version text)",
     f"SELECT s.article_id, s.summary_text, s.model_version FROM news_summaries s JOIN news_articles a ON a.id = s.article_id "
     f"WHERE s.valid_to IS NULL AND {COMPLETE}"),
    ("s_terms(id bigint, name text, path text, definition text, simple_definition text, difficulty_level int)",
     f"SELECT DISTINCT t.id, t.name, t.path::text, t.definition, t.simple_definition, t.difficulty_level FROM financial_terms t "
     f"JOIN article_term_highlights h ON h.term_id = t.id JOIN news_articles a ON a.id = h.article_id WHERE {COMPLETE}"),
    ("s_highlights(article_id bigint, term_id bigint, position_start int, position_end int)",
     f"SELECT h.article_id, h.term_id, h.position_start, h.position_end FROM article_term_highlights h "
     f"JOIN news_articles a ON a.id = h.article_id WHERE {COMPLETE}"),
    ("s_card_news(article_id bigint, total_cards int, status text, model_version text, prompt_version text)",
     f"SELECT c.article_id, c.total_cards, c.status, c.model_version, c.prompt_version FROM card_news c "
     f"JOIN news_articles a ON a.id = c.article_id WHERE {COMPLETE}"),
    ("s_cards(article_id bigint, card_order int, template_type text, title text, body text, sub_body text, "
     "highlight text, image_url text, source_sentence_ids text)",
     f"SELECT c.article_id, x.card_order, x.template_type, x.title, x.body, x.sub_body, x.highlight, x.image_url, "
     f"x.source_sentence_ids::text FROM card_news_cards x JOIN card_news c ON c.id = x.card_news_id "
     f"JOIN news_articles a ON a.id = c.article_id WHERE {COMPLETE}"),
    ("s_quizzes(article_id bigint, quiz_order int, question text, choices text, answer_index int, explanation text, "
     "source_sentence_ids text, model_version text, prompt_version text)",
     f"SELECT q.article_id, q.quiz_order, q.question, q.choices::text, q.answer_index, q.explanation, "
     f"q.source_sentence_ids::text, q.model_version, q.prompt_version FROM quizzes q JOIN news_articles a ON a.id = q.article_id "
     f"WHERE {COMPLETE}"),
]

MERGE = """
CREATE TEMP TABLE map AS
  SELECT s.id AS src_id FROM s_articles s
  WHERE NOT EXISTS (SELECT 1 FROM news_articles a WHERE a.source_url = s.source_url);
INSERT INTO news_articles (source_url, source_name, title, content, category, published_at, crawled_at, embedding, thumbnail_url)
  SELECT s.source_url, s.source_name, s.title, s.content, s.category, s.published_at, s.crawled_at,
         s.embedding::vector, s.thumbnail_url
  FROM s_articles s JOIN map m ON m.src_id = s.id;
ALTER TABLE map ADD COLUMN dst_id bigint;
UPDATE map m SET dst_id = a.id FROM s_articles s JOIN news_articles a ON a.source_url = s.source_url WHERE s.id = m.src_id;

INSERT INTO financial_terms (name, path, definition, simple_definition, difficulty_level)
  SELECT name, path::ltree, definition, simple_definition, difficulty_level FROM s_terms
  ON CONFLICT (name) DO NOTHING;
CREATE TEMP TABLE tmap AS SELECT s.id AS src_id, t.id AS dst_id FROM s_terms s JOIN financial_terms t ON t.name = s.name;

INSERT INTO news_summaries (article_id, summary_text, summary_version, model_version)
  SELECT m.dst_id, s.summary_text, 1, s.model_version FROM s_summaries s JOIN map m ON m.src_id = s.article_id;
INSERT INTO article_term_highlights (article_id, term_id, position_start, position_end)
  SELECT m.dst_id, t.dst_id, h.position_start, h.position_end
  FROM s_highlights h JOIN map m ON m.src_id = h.article_id JOIN tmap t ON t.src_id = h.term_id;
INSERT INTO card_news (article_id, total_cards, status, model_version, prompt_version)
  SELECT m.dst_id, c.total_cards, c.status, c.model_version, c.prompt_version FROM s_card_news c JOIN map m ON m.src_id = c.article_id;
INSERT INTO card_news_cards (card_news_id, card_order, template_type, title, body, sub_body, highlight, image_url, source_sentence_ids)
  SELECT cn.id, x.card_order, x.template_type, x.title, x.body, x.sub_body, x.highlight, x.image_url, x.source_sentence_ids::jsonb
  FROM s_cards x JOIN map m ON m.src_id = x.article_id JOIN card_news cn ON cn.article_id = m.dst_id;
INSERT INTO quizzes (article_id, quiz_order, question, choices, answer_index, explanation, source_sentence_ids, model_version, prompt_version)
  SELECT m.dst_id, q.quiz_order, q.question, q.choices::jsonb, q.answer_index, q.explanation, q.source_sentence_ids::jsonb,
         q.model_version, q.prompt_version
  FROM s_quizzes q JOIN map m ON m.src_id = q.article_id;
-- 생성 상태 API(/api/news/{id}/generation)가 완료로 보이도록 작업 기록을 남긴다. AI 작업자가 다시 처리하지 않는다.
INSERT INTO ai_jobs (article_id, kind, status, attempts)
  SELECT m.dst_id, k.kind, 'SUCCEEDED', 0 FROM map m CROSS JOIN (VALUES ('ARTICLE'), ('CARD'), ('QUIZ')) k(kind);

SELECT 'source_complete_articles', count(*) FROM s_articles
UNION ALL SELECT 'skipped_already_in_target', count(*) FROM s_articles s WHERE s.id NOT IN (SELECT src_id FROM map)
UNION ALL SELECT 'imported_articles', count(*) FROM map
UNION ALL SELECT 'imported_quizzes', count(*) FROM s_quizzes q JOIN map m ON m.src_id = q.article_id
UNION ALL SELECT 'imported_cards', count(*) FROM s_cards x JOIN map m ON m.src_id = x.article_id
UNION ALL SELECT 'terms_in_source', count(*) FROM s_terms;
"""


def psql(container, user, db):
    return ['docker', 'exec', '-i', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db]


def export(args, select):
    out = subprocess.run(psql(args.source_container, args.source_user, args.source_db)
                         + ['-c', f'COPY ({select}) TO STDOUT'], capture_output=True, text=True, check=True)
    return out.stdout


def build_script(exports, dry_run):
    """원본에서 받은 COPY 텍스트(TABLES 순서)로 대상 DB에서 실행할 SQL을 만든다."""
    script = ['BEGIN;']
    for (ddl, _), data in zip(TABLES, exports, strict=True):
        script.append(f'CREATE TEMP TABLE {ddl} ON COMMIT DROP;')
        script.append(f'COPY {ddl.split("(")[0]} FROM STDIN;')
        data = data.rstrip('\n')
        if data:  # 빈 줄은 '빈 값 1행'으로 읽히므로 데이터가 있을 때만 넣는다
            script.append(data)
        script.append('\\.')
    script.append(MERGE)
    script.append('ROLLBACK;' if dry_run else 'COMMIT;')
    return '\n'.join(script) + '\n'


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--source-container', default='youthletter-pipeline-postgres-1')
    p.add_argument('--source-db', default='youthletter_pipeline')
    p.add_argument('--source-user', default='youthletter')
    p.add_argument('--target-container', required=True)
    p.add_argument('--target-db', required=True)
    p.add_argument('--target-user', required=True)
    p.add_argument('--dry-run', action='store_true', help='개수만 확인하고 ROLLBACK')
    args = p.parse_args()
    if (args.source_container, args.source_db) == (args.target_container, args.target_db):
        sys.exit('원본과 대상이 같은 DB입니다.')

    script = build_script([export(args, select) for _, select in TABLES], args.dry_run)
    result = subprocess.run(psql(args.target_container, args.target_user, args.target_db) + ['-A', '-t', '-F', '\t'],
                            input=script, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit('가져오기 실패(대상 DB는 변경되지 않음):\n' + result.stderr.strip())
    for line in result.stdout.strip().splitlines():
        if '\t' in line:
            name, value = line.split('\t', 1)
            print(f'{name}: {value}')
    print('DRY RUN: 변경 사항을 되돌렸습니다.' if args.dry_run else '완료: 대상 DB에 저장했습니다.')


if __name__ == '__main__':
    main()
