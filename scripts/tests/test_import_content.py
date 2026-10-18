import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('import_content', Path(__file__).parents[1] / 'import_content.py')
ic = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ic)


class BuildScriptTest(unittest.TestCase):
    def exports(self, articles='1\turl\n'):
        return [articles] + [''] * (len(ic.TABLES) - 1)

    def test_dry_run_rolls_back_and_real_run_commits(self):
        self.assertTrue(ic.build_script(self.exports(), True).rstrip().endswith('ROLLBACK;'))
        self.assertTrue(ic.build_script(self.exports(), False).rstrip().endswith('COMMIT;'))

    def test_every_table_gets_a_copy_block_and_empty_tables_add_no_blank_row(self):
        script = ic.build_script(self.exports(), False)
        self.assertEqual(script.count(' FROM STDIN;'), len(ic.TABLES))
        self.assertEqual(script.count('\n\\.\n'), len(ic.TABLES))
        self.assertNotIn('FROM STDIN;\n\n', script)
        self.assertIn('FROM STDIN;\n1\turl\n\\.', script)

    def test_only_new_articles_are_imported_and_existing_terms_are_kept(self):
        self.assertIn('WHERE NOT EXISTS (SELECT 1 FROM news_articles a WHERE a.source_url = s.source_url)', ic.MERGE)
        self.assertIn('ON CONFLICT (name) DO NOTHING', ic.MERGE)


if __name__ == '__main__':
    unittest.main()
