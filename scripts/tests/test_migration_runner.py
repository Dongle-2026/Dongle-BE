import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).parents[2] / "docker/postgres/02-apply-migrations.sh"


class MigrationRunnerTest(unittest.TestCase):
    def run_fixture(self, fail=None):
        with tempfile.TemporaryDirectory(prefix="migration path with spaces ") as root:
            root=Path(root);directory=root/"sql files";directory.mkdir()
            capstone=root/"capstone.sql";capstone.write_text("BEGIN;\nCOMMIT;\n")
            for name,sql in {"V9__old.sql":"SELECT 1;", "V16__first.sql":"SELECT 1;",
                             "V27__report.sql":"DO $$\nBEGIN\nEND $$;", "V100__future.sql":"BEGIN;\nCOMMIT;"}.items():
                (directory/name).write_text(sql)
            binary=root/"bin";binary.mkdir();log=root/"calls.jsonl"
            fake=binary/"psql"
            fake.write_text('''#!/usr/bin/env python3
import json,os,sys
with open(os.environ['PSQL_TEST_LOG'],'a') as log:
    log.write(json.dumps(sys.argv[1:])+'\\n')
sys.exit(2 if os.environ.get('FAIL_SQL') and sys.argv[-1].endswith(os.environ['FAIL_SQL']) else 0)
''')
            fake.chmod(0o755)
            env={**os.environ,"PATH":str(binary)+os.pathsep+os.environ["PATH"],
                 "CAPSTONE_SQL":str(capstone),"DB_MIGRATIONS_DIR":str(directory),"PSQL_TEST_LOG":str(log)}
            if fail: env["FAIL_SQL"]=fail
            result=subprocess.run(["sh",str(RUNNER)],env=env,capture_output=True,text=True)
            calls=[json.loads(line) for line in log.read_text().splitlines()]
            return result.returncode,calls

    def test_numeric_order_space_paths_and_do_blocks_use_a_file_transaction(self):
        status,calls=self.run_fixture()
        self.assertEqual(status,0)
        self.assertEqual([Path(c[-1]).name for c in calls],["capstone.sql","V16__first.sql","V27__report.sql","V100__future.sql"])
        self.assertIn("-1",calls[2])  # DO 안의 BEGIN은 트랜잭션 시작문이 아니다.
        self.assertNotIn("-1",calls[3])

    def test_failed_migration_stops_before_later_files(self):
        status,calls=self.run_fixture("V27__report.sql")
        self.assertNotEqual(status,0)
        self.assertEqual(Path(calls[-1][-1]).name,"V27__report.sql")
