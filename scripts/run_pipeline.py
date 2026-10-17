#!/usr/bin/env python3
"""한경 수집 → 원문/요약/카드뉴스/퀴즈 DB 저장. Ctrl+C로 안전하게 중지."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import secrets
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / '.env.pipeline.local'
COMPOSE = ['docker', 'compose', '--project-name', 'youthletter-pipeline',
           '--env-file', str(ENV_FILE), '-f', str(ROOT / 'docker-compose.pipeline.yml')]
STATUS_SQL = """
SELECT json_build_object(
 'run', (SELECT row_to_json(r) FROM (SELECT id,status,saved_count,failed_count FROM content_pipeline_runs ORDER BY id DESC LIMIT 1) r),
 'articles', (SELECT count(*) FROM news_articles),
 'summaries', (SELECT count(DISTINCT article_id) FROM news_summaries WHERE valid_to IS NULL),
 'cardNews', (SELECT count(*) FROM card_news WHERE status='PUBLISHED'),
 'quizzes', (SELECT count(*) FROM quizzes),
 'termsReady', (SELECT count(*) FROM (SELECT article_id FROM article_term_highlights GROUP BY article_id HAVING count(DISTINCT term_id)>=5) t),
 'completeArticles', (SELECT count(*) FROM news_articles a
   WHERE EXISTS(SELECT 1 FROM news_summaries s WHERE s.article_id=a.id AND s.valid_to IS NULL)
   AND EXISTS(SELECT 1 FROM card_news c WHERE c.article_id=a.id AND c.status='PUBLISHED')
   AND (SELECT count(*) FROM quizzes q WHERE q.article_id=a.id)>=3
   AND (SELECT count(DISTINCT term_id) FROM article_term_highlights h WHERE h.article_id=a.id)>=5),
 'pending', (SELECT count(*) FROM ai_jobs WHERE status IN ('QUEUED','RUNNING')),
 'succeeded', (SELECT count(*) FROM ai_jobs WHERE status='SUCCEEDED'),
 'failed', (SELECT count(*) FROM ai_jobs WHERE status='FAILED'),
 'errors', (SELECT coalesce(json_agg(e),'[]'::json) FROM
   (SELECT article_id,kind,error_code FROM ai_jobs WHERE status='FAILED' ORDER BY id DESC LIMIT 10) e)
)::text;
"""


def command(args, capture=False, check=True, **kwargs):
    return subprocess.run(args, cwd=ROOT, text=True, check=check,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None, **kwargs)


def compose(*args, **kwargs):
    return command(COMPOSE + list(args), **kwargs)


def sql(query):
    return compose('exec', '-T', 'postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                   '-U', 'youthletter', '-d', 'youthletter_pipeline', '-Atc', query,
                   capture=True, timeout=30).stdout.strip()


def status():
    return json.loads(sql(STATUS_SQL))


def describe(s):
    return (f"원문 {s['articles']} | 요약 {s['summaries']} | 카드뉴스 {s['cardNews']}세트 | "
            f"퀴즈 {s['quizzes']}문항 | 용어 5개 이상 {s.get('termsReady', 0)}기사 | 전체 완성 {s.get('completeArticles', 0)}기사 | "
            f"대기/진행 {s['pending']} | 성공 {s['succeeded']} | 실패 {s['failed']} | "
            f"수집 {s['run']}")


def done(s, previous_run):
    run = s['run']
    return bool(run and run['id'] > previous_run and run['status'] != 'RUNNING' and s['pending'] == 0)


def succeeded(s):
    return (s['run']['status'] == 'SUCCEEDED' and s['failed'] == 0
            and ('completeArticles' not in s or s['completeArticles'] == s['articles']))


def backfill_incomplete_articles():
    # 이전 버전에서 용어 2개만 저장한 기사도 보완한다. 기존 결과는 새 결과 저장 시까지 보존한다.
    sql("""UPDATE ai_jobs j SET status='QUEUED',attempts=0,error_code=NULL,
        lease_token=NULL,lease_until=NULL,available_at=now(),updated_at=now()
        WHERE kind='ARTICLE' AND status IN ('SUCCEEDED','FAILED','RUNNING')
        AND id=(SELECT max(k.id) FROM ai_jobs k WHERE k.article_id=j.article_id AND k.kind='ARTICLE')
        AND NOT EXISTS(SELECT 1 FROM ai_jobs a WHERE a.article_id=j.article_id AND a.kind='ARTICLE'
                       AND a.status='QUEUED')
        AND ((SELECT count(DISTINCT term_id) FROM article_term_highlights h WHERE h.article_id=j.article_id)<5
             OR NOT EXISTS(SELECT 1 FROM news_summaries s WHERE s.article_id=j.article_id AND s.valid_to IS NULL)
             OR NOT EXISTS(SELECT 1 FROM card_news c WHERE c.article_id=j.article_id AND c.status='PUBLISHED')
             OR (SELECT count(*) FROM quizzes q WHERE q.article_id=j.article_id)<3);
        INSERT INTO ai_jobs(article_id,kind) SELECT id,'ARTICLE' FROM news_articles a
        WHERE NOT EXISTS(SELECT 1 FROM ai_jobs j WHERE j.article_id=a.id AND j.kind='ARTICLE');""")


def configure():
    if not ENV_FILE.exists():
        values = {'PIPELINE_DB_PASSWORD': secrets.token_hex(24),
                  'PIPELINE_AI_TOKEN': secrets.token_hex(24),
                  'PIPELINE_JWT_SECRET': secrets.token_hex(32)}
        fd = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as output:
            output.write(''.join(f'{key}={value}\n' for key, value in values.items()))
    # 모델과 포트 설정도 같은 파일에서 수정 가능. 셸 코드를 실행하지 않는다.
    for line in ENV_FILE.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            os.environ.setdefault(key.strip(), value.strip())


def stage_migrations():
    """Docker가 Desktop 파일을 직접 읽지 않도록 완전한 로컬 사본을 준비한다."""
    sources = {
        'init/init.sql': ROOT / 'docker/postgres/init.sql',
        'init/02-apply-migrations.sh': ROOT / 'docker/postgres/02-apply-migrations.sh',
        'init/upgrade-capstone.sql': ROOT / 'docker/postgres/upgrade-capstone.sql',
    }
    migrations = list((ROOT / 'src/main/resources/db/migration').glob('V*.sql'))
    if not migrations:
        raise RuntimeError('마이그레이션 SQL 파일을 찾을 수 없습니다.')
    sources.update({f'migrations/{path.name}': path for path in migrations})
    # 모든 원본 읽기에 성공한 뒤에만 Docker 구성을 바꾼다.
    contents = {name: path.read_bytes() for name, path in sources.items()}
    digest = hashlib.sha256()
    for name, data in sorted(contents.items()):
        digest.update(name.encode() + b'\0' + data + b'\0')
    cache = Path(tempfile.gettempdir()).resolve() / f'youthletter-pipeline-{os.getuid()}' / digest.hexdigest()
    for name, data in contents.items():
        target = cache / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
        target.chmod(0o644)
    # postgres는 런처 종료 후에도 살아 있으므로 마운트 사본을 유지한다.
    os.environ['PIPELINE_INIT_DIR'] = str(cache / 'init')
    os.environ['PIPELINE_MIGRATIONS_DIR'] = str(cache / 'migrations')


def ready(check, seconds, label):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (OSError, ValueError, subprocess.SubprocessError):
            pass
        time.sleep(2)
    raise RuntimeError(f'{label} 준비 시간 초과. logs/pipeline-launcher.log와 컨테이너 로그를 확인하세요.')


def ollama_tags():
    with urllib.request.urlopen('http://127.0.0.1:11434/api/tags', timeout=5) as response:
        return json.load(response)['models']


def ensure_runtime(log):
    for executable in ('docker', 'ollama'):
        if not shutil.which(executable):
            raise RuntimeError(f'{executable} 설치가 필요합니다. Docker Desktop과 Ollama를 설치한 뒤 다시 실행하세요.')
    if command(['docker', 'info'], capture=True, check=False).returncode:
        if sys.platform == 'darwin':
            command(['open', '-a', 'Docker'])
        ready(lambda: command(['docker', 'info'], capture=True, check=False).returncode == 0, 180, 'Docker')
    command(['docker', 'compose', 'version'], capture=True)
    try:
        ollama_tags()
    except (OSError, ValueError):
        # 모델 서버는 다음 실행에서도 재사용한다. 로그와 stdin을 터미널에서 분리한다.
        subprocess.Popen(['ollama', 'serve'], cwd=ROOT, stdin=subprocess.DEVNULL,
                         stdout=log, stderr=log, start_new_session=True)
        ready(lambda: isinstance(ollama_tags(), list), 60, 'Ollama')
    names = {item['name'] for item in ollama_tags()}
    for model in (os.environ.get('OLLAMA_CHAT_MODEL', 'gemma3:4b'),
                  os.environ.get('OLLAMA_EMBED_MODEL', 'nomic-embed-text')):
        normalized = model if ':' in model else model + ':latest'
        if normalized not in names:
            print(f'모델 다운로드: {model} (최초 실행 시 시간이 걸립니다)')
            command(['ollama', 'pull', model])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--once', action='store_true', help='시작 시 한 번 수집하고 대기열 처리가 끝나면 종료')
    parser.add_argument('--limit', type=int, default=0, help='수집 회차당 신규 기사 수. 기본 0=목록에 있는 전체')
    parser.add_argument('--timeout', type=int, default=21600, help='--once 최대 대기 초, 기본 6시간')
    parser.add_argument('--status', action='store_true', help='저장/작업 현황만 표시')
    parser.add_argument('--stop', action='store_true', help='전용 컨테이너 중지. DB 데이터 보존')
    parser.add_argument('--retry-failed', action='store_true', help='시작 시 실패한 AI 작업만 다시 접수')
    parser.add_argument('--no-build', action='store_true', help='이미 빌드한 이미지 사용')
    args = parser.parse_args()
    if args.limit < 0 or args.timeout <= 0:
        parser.error('limit은 0 이상, timeout은 양수여야 합니다.')
    os.chdir(ROOT)
    configure()
    if args.status:
        current = status()
        print(describe(current))
        print(json.dumps(current['errors'], ensure_ascii=False))
        return 0
    if args.stop:
        lock_path = ROOT / 'logs/pipeline.lock'
        if lock_path.exists():
            with lock_path.open('r') as active_lock:
                try:
                    fcntl.flock(active_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    owner = active_lock.read().strip()
                    if owner.isdigit():
                        try:
                            os.kill(int(owner), signal.SIGINT)
                        except ProcessLookupError:
                            pass
        compose('stop')
        return 0
    (ROOT / 'logs').mkdir(exist_ok=True)
    with (ROOT / 'logs/pipeline.lock').open('a+') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('이미 실행 중입니다. 다른 터미널에서 ./run-pipeline --status 로 확인하세요.')
        lock.seek(0)
        lock.truncate()
        lock.write(str(os.getpid()))
        lock.flush()
        sleep_guard = None
        started = False
        try:
            if sys.platform == 'darwin':
                sleep_guard = subprocess.Popen(['caffeinate', '-i', '-w', str(os.getpid())])
            with (ROOT / 'logs/pipeline-launcher.log').open('a') as log:
                print('Docker·Ollama·모델 준비 중…')
                ensure_runtime(log)
            os.environ['PIPELINE_LIMIT'] = str(args.limit)
            if args.once:
                os.environ['PIPELINE_INTERVAL_MS'] = str((args.timeout + 60) * 1000)
            print('전용 DB와 AI 서비스를 준비합니다. 기존 개발 DB는 사용하지 않습니다.')
            stage_migrations()
            compose('stop', 'backend', 'ai-service')
            compose('up', '-d', '--wait', 'postgres', 'redis')
            started = True
            # 기존 DB의 누적 마이그레이션도 실행한다. 앱을 먼저 멈춰 스키마 변경과 겹치지 않게 한다.
            compose('stop', 'backend')
            compose('exec', '-T', 'postgres', 'sh', '/docker-entrypoint-initdb.d/02-apply-migrations.sh')
            backfill_incomplete_articles()
            previous_run = int(sql('SELECT coalesce(max(id),0) FROM content_pipeline_runs'))
            if args.retry_failed:
                sql("""UPDATE ai_jobs j SET status='QUEUED',attempts=0,error_code=NULL,
                    available_at=now(),updated_at=now()
                    WHERE status='FAILED' AND id=(SELECT max(k.id) FROM ai_jobs k
                        WHERE k.article_id=j.article_id AND k.kind=j.kind)
                    AND NOT EXISTS(SELECT 1 FROM ai_jobs a WHERE a.article_id=j.article_id
                        AND a.kind=j.kind AND a.status IN ('QUEUED','RUNNING'))""")
            build = [] if args.no_build else ['--build']
            compose('up', '-d', *build, 'ai-service')
            # 컨테이너에서 호스트 모델에 접근 가능한지 검증한다. 모델 API는 외부 공개하지 않는다.
            probe = "fetch(process.env.OLLAMA_BASE_URL+'/api/tags',{signal:AbortSignal.timeout(5000)}).then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"
            ready(lambda: compose('exec', '-T', 'ai-service', 'node', '-e', probe, capture=True, check=False).returncode == 0,
                  60, 'AI 서비스 → Ollama 연결')
            compose('up', '-d', '--wait', '--wait-timeout', '180', *build, 'backend')
            print(f"실행 중: http://localhost:{os.environ.get('PIPELINE_API_PORT', '18082')} | DB: localhost:{os.environ.get('PIPELINE_DB_PORT', '15436')}/youthletter_pipeline")
            print('원문 수집 후 AI 생성 결과를 순차 저장합니다. Ctrl+C로 중지해도 DB와 대기열은 보존됩니다.')
            deadline = time.monotonic() + args.timeout
            previous = None
            while True:
                if args.once and time.monotonic() > deadline:
                    raise RuntimeError('완료 대기 시간 초과. DB에 남은 작업은 다음 실행에서 이어집니다.')
                try:
                    current = status()
                except (subprocess.SubprocessError, OSError, ValueError):
                    # 일시적인 Docker CLI/DB 연결 실패가 생성 작업까지 멈추게 하지 않는다.
                    print('진행 현황 조회를 잠시 실패했습니다. 실행 중인 작업을 유지하고 10초 후 다시 확인합니다.')
                    time.sleep(10)
                    continue
                if current != previous:
                    print(describe(current))
                    if current['errors'] and (previous is None or current['errors'] != previous['errors']):
                        print('실패 상세:', json.dumps(current['errors'], ensure_ascii=False))
                    previous = current
                if args.once and done(current, previous_run):
                    if succeeded(current):
                        print('수집 및 AI 대기열 처리 완료. 결과가 DB에 저장되었습니다.')
                        return 0
                    print('실패/중단 내역이 있습니다. 저장된 결과는 보존하며 --retry-failed로 AI 실패 작업을 다시 시도할 수 있습니다.')
                    return 1
                if args.once and time.monotonic() > deadline:
                    raise RuntimeError('완료 대기 시간 초과. DB에 남은 작업은 다음 실행에서 이어집니다.')
                time.sleep(10)
        except KeyboardInterrupt:
            print('\n중지합니다. 다음 실행 시 미완료 작업을 이어갑니다.')
            return 130
        finally:
            try:
                if started:
                    # DB는 현황 조회를 위해 유지. AI 작업자는 정지한다.
                    stopped = compose('stop', 'backend', 'ai-service', check=False, timeout=60)
                    if stopped.returncode:
                        print('컨테이너 중지를 확인하지 못했습니다. ./run-pipeline --stop 으로 다시 중지하세요.', file=sys.stderr)
            finally:
                if sleep_guard:
                    sleep_guard.terminate()
                    sleep_guard.wait()


if __name__ == '__main__':
    try:
        sys.exit(main())
    except subprocess.CalledProcessError as error:
        print(f'실행 명령 실패 (종료 코드 {error.returncode}). 로그와 Docker 상태를 확인하세요.', file=sys.stderr)
        if error.stderr:
            print(error.stderr[-2000:], file=sys.stderr)
        sys.exit(1)
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print(f'실행 실패: {error}', file=sys.stderr)
        sys.exit(1)
