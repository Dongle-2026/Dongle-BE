#!/bin/sh
# 새 DB 초기화 때(init.sql 다음) 추가 스키마를 순서대로 적용한다.
#   upgrade-capstone.sql → src/main/resources/db/migration/V16~ (버전 순)
# 모든 파일은 다시 실행해도 안전하다(IF NOT EXISTS). 이미 만들어진 로컬 DB에도 아래처럼 수동 적용할 수 있다.
#   docker exec youthletter-postgres sh /docker-entrypoint-initdb.d/02-apply-migrations.sh
set -e
DB="${POSTGRES_DB:-youthletter}"
DB_USER="${POSTGRES_USER:-youthletter}"
run() { psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB" "$@"; }

run -f /migrations/upgrade-capstone.sql
for f in $(ls /migrations/db/V*.sql | sort -V); do
  version=$(basename "$f" | sed -E 's/^V([0-9]+)__.*/\1/')
  if [ "$version" -ge 16 ]; then
    echo "apply $(basename "$f")"
    # 파일 자체에 BEGIN이 있으면 그대로, 없으면 파일 하나를 한 트랜잭션으로 실행
    if grep -qi '^BEGIN' "$f"; then run -f "$f" > /dev/null; else run -1 -f "$f" > /dev/null; fi
  fi
done
