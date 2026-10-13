import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
spec = importlib.util.spec_from_file_location('db_archive', Path(__file__).parents[1] / 'db_archive.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ArchiveTest(unittest.TestCase):
    def test_backup_protects_existing_file_and_removes_partial_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'backup.dump'
            path.write_bytes(b'original')
            with self.assertRaises(FileExistsError), patch.object(module.subprocess, 'run') as run:
                module.archive(['docker', 'compose'], 'backup', path)
            run.assert_not_called()
            self.assertEqual(path.read_bytes(), b'original')
            path.unlink()
            with self.assertRaises(subprocess.CalledProcessError), patch.object(module.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'pg_dump')):
                module.archive(['docker', 'compose'], 'backup', path)
            self.assertFalse(path.exists())

    def test_restore_refuses_existing_database_before_pg_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'backup.dump'
            path.write_bytes(b'archive')
            with self.assertRaises(subprocess.CalledProcessError), patch.object(module.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'createdb')) as run:
                module.archive(['docker', 'compose'], 'restore', path, 'existing')
            self.assertEqual(run.call_count, 1)
            self.assertIn('createdb', str(run.call_args))
            with self.assertRaises(ValueError), patch.object(module.subprocess, 'run') as run:
                module.archive(['docker', 'compose'], 'restore', path, 'db;drop')
            run.assert_not_called()

    def test_restore_is_atomic_and_backup_is_private(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'backup.dump'
            with patch.object(module.subprocess, 'run'):
                module.archive(['docker', 'compose'], 'backup', path)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with patch.object(module.subprocess, 'run') as run:
                module.archive(['docker', 'compose'], 'restore', path, 'rehearsal')
            self.assertEqual(run.call_count, 2)
            self.assertIn('--single-transaction', str(run.call_args))
