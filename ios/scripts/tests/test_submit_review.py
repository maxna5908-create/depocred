import importlib.util
from pathlib import Path
import unittest
s=importlib.util.spec_from_file_location('review',Path(__file__).parents[1]/'submit_review.py')
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class ReviewTests(unittest.TestCase):
    def test_exact_build_and_explicit_automatic_release(self):
        summary={'bundle_id':'com.depocred.app','app_store_id':'6766194530','version':'1.0.37','build':'37.123.1'}
        command=m.command(summary,'key.json')
        for flag,value in [('--build_number','37.123.1'),('--app_version','1.0.37'),('--submit_for_review','true'),('--automatic_release','true'),('--skip_binary_upload','true'),('--skip_screenshots','true')]:
            self.assertEqual(command[command.index(flag)+1],value)
    def test_wrong_app_never_submitted(self):
        with self.assertRaises(ValueError):
            m.command({'bundle_id':'wrong','app_store_id':'6766194530'},'key.json')
if __name__=='__main__': unittest.main()
