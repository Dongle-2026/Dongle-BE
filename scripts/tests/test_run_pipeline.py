import importlib.util
from pathlib import Path
import unittest
import tempfile
import os
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('run_pipeline', Path(__file__).parents[1] / 'run_pipeline.py')
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)

class CompletionTest(unittest.TestCase):
    def test_does_not_report_empty_queue_before_new_crawl_as_complete(self):
        self.assertFalse(pipeline.done({'run': None, 'pending': 0}, 0))
        self.assertFalse(pipeline.done({'run': {'id': 1, 'status': 'SUCCEEDED'}, 'pending': 0}, 1))
        self.assertFalse(pipeline.done({'run': {'id': 2, 'status': 'RUNNING'}, 'pending': 0}, 1))
        self.assertFalse(pipeline.done({'run': {'id': 2, 'status': 'SUCCEEDED'}, 'pending': 1}, 1))
        self.assertTrue(pipeline.done({'run': {'id': 2, 'status': 'SUCCEEDED'}, 'pending': 0}, 1))

    def test_failure_is_not_success(self):
        self.assertFalse(pipeline.succeeded({'run': {'status': 'FAILED'}, 'failed': 0}))
        self.assertFalse(pipeline.succeeded({'run': {'status': 'SUCCEEDED'}, 'failed': 1}))
        self.assertTrue(pipeline.succeeded({'run': {'status': 'SUCCEEDED'}, 'failed': 0}))
        self.assertFalse(pipeline.succeeded({'run': {'status': 'SUCCEEDED'}, 'failed': 0,
                                            'articles': 2, 'completeArticles': 1}))

class ConfigurationTest(unittest.TestCase):
    def test_migration_snapshot_is_complete_reusable_and_changes_with_sql(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'repo'
            postgres = root / 'docker/postgres'
            postgres.mkdir(parents=True)
            for name in ('init.sql', '02-apply-migrations.sh', 'upgrade-capstone.sql'):
                (postgres / name).write_text(name)
            migrations = root / 'src/main/resources/db/migration'
            migrations.mkdir(parents=True)
            source = migrations / 'V16__ai_jobs.sql'
            source.write_text('SELECT 1;')
            with patch.object(pipeline, 'ROOT', root), \
                    patch.object(pipeline.tempfile, 'gettempdir', return_value=directory), \
                    patch.dict(os.environ, {}, clear=True):
                pipeline.stage_migrations()
                snapshot = Path(os.environ['PIPELINE_MIGRATIONS_DIR'])
                self.assertEqual((snapshot / source.name).read_bytes(), source.read_bytes())
                self.assertEqual((Path(os.environ['PIPELINE_INIT_DIR']) / 'init.sql').read_text(), 'init.sql')
                pipeline.stage_migrations()
                self.assertEqual(str(snapshot), os.environ['PIPELINE_MIGRATIONS_DIR'])
                source.write_text('SELECT 2;')
                pipeline.stage_migrations()
                self.assertNotEqual(str(snapshot), os.environ['PIPELINE_MIGRATIONS_DIR'])
                self.assertEqual((snapshot / source.name).read_text(), 'SELECT 1;')

    def test_credentials_are_private_and_reused_on_next_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env.pipeline.local'
            with patch.object(pipeline, 'ENV_FILE', path), patch.dict(os.environ, {}, clear=True):
                pipeline.configure()
                original = path.read_text()
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                self.assertIn('PIPELINE_DB_PASSWORD=', original)
                pipeline.configure()
                self.assertEqual(path.read_text(), original)
                self.assertEqual(len(os.environ['PIPELINE_JWT_SECRET']), 64)

class LauncherTest(unittest.TestCase):
    def test_temporary_status_failure_does_not_stop_jobs_or_report_false_failure(self):
        import subprocess
        from types import SimpleNamespace
        from contextlib import ExitStack
        state = {'run': {'id': 1, 'status': 'SUCCEEDED'}, 'articles': 1, 'summaries': 1,
                 'cardNews': 1, 'quizzes': 3, 'pending': 0, 'succeeded': 3, 'failed': 0, 'errors': []}
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            stack.enter_context(patch.object(pipeline, 'ROOT', Path(directory)))
            stack.enter_context(patch.object(pipeline, 'configure'))
            stack.enter_context(patch.object(pipeline, 'ensure_runtime'))
            stack.enter_context(patch.object(pipeline, 'stage_migrations'))
            stack.enter_context(patch.object(pipeline, 'ready'))
            stack.enter_context(patch.object(pipeline.os, 'chdir'))
            stack.enter_context(patch.object(pipeline.time, 'sleep'))
            stack.enter_context(patch.object(pipeline.sys, 'platform', 'linux'))
            stack.enter_context(patch.object(pipeline.sys, 'argv', ['run-pipeline', '--once', '--no-build']))
            stack.enter_context(patch.dict(os.environ, {}, clear=True))
            stack.enter_context(patch.object(pipeline, 'sql', return_value='0'))
            stack.enter_context(patch.object(pipeline, 'status', side_effect=[subprocess.CalledProcessError(-9, ['docker']), state]))
            compose = stack.enter_context(patch.object(pipeline, 'compose', return_value=SimpleNamespace(returncode=0)))
            self.assertEqual(pipeline.main(), 0)
            compose.assert_any_call('stop', 'backend', 'ai-service', check=False, timeout=60)
