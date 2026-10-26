import base64
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

try:
    import requests
except ImportError:
    sys.modules['requests'] = types.ModuleType('requests')
spec = importlib.util.spec_from_file_location('publisher', Path(__file__).parents[1] / 'publish-rustore.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

class PublishTests(unittest.TestCase):
    def test_signature_pem_and_base64_der(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        for encoding in (serialization.Encoding.PEM, serialization.Encoding.DER):
            raw = key.private_bytes(encoding, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
            content = raw.decode() if encoding == serialization.Encoding.PEM else base64.b64encode(raw).decode()
            body = p.signature('test-id', content)
            key.public_key().verify(base64.b64decode(body['signature']), ('test-id' + body['timestamp']).encode(), padding.PKCS1v15(), hashes.SHA512())

    def test_existing_draft_is_never_replaced(self):
        api = unittest.mock.Mock()
        api.request.side_effect = [
            {'content': [{'versionId': 1, 'versionCode': 26, 'versionStatus': 'ACTIVE'}, {'versionId': 2, 'versionCode': 0, 'versionStatus': 'DRAFT'}], 'totalPages': 1},
            {'appId': p.APP_ID}]
        args = types.SimpleNamespace(metadata=Path('.'), images=Path('.'), apk=Path(__file__), version_code=35, resume_version=0)
        with patch.object(p, 'API', return_value=api), patch.object(p, 'metadata', return_value={}), patch.object(p, 'prepare_images', return_value=[]):
            with self.assertRaisesRegex(RuntimeError, 'Existing draft preserved'):
                p.publish(args)
        self.assertTrue(all(c.args[0] == 'GET' for c in api.request.call_args_list))
        api.upload.assert_not_called()

    def test_already_submitted_is_noop(self):
        api = unittest.mock.Mock()
        api.request.return_value = {'content': [{'versionId': 3, 'versionCode': 35, 'versionStatus': 'MODERATION'}], 'totalPages': 1}
        args = types.SimpleNamespace(metadata=Path('.'), images=Path('.'), apk=Path(__file__), version_code=35, resume_version=0)
        with patch.object(p, 'API', return_value=api), patch.object(p, 'metadata', return_value={}), patch.object(p, 'prepare_images', return_value=[]):
            p.publish(args)
        self.assertEqual(api.request.call_count, 1)
        api.upload.assert_not_called()

    def test_resume_keeps_apk_and_checks_rollout_via_status(self):
        api = unittest.mock.Mock()
        payload = {'appName': 'DepoCred', 'shortDescription': 'short', 'fullDescription': 'full', 'publishType': 'INSTANTLY', 'partialValue': 100}
        draft = dict(payload, partialValue=-1, packages=[])
        api.request.side_effect = [
            {'content': [{'versionId': 1, 'versionCode': 26, 'versionStatus': 'ACTIVE'}, {'versionId': 2, 'versionCode': 35, 'versionStatus': 'DRAFT'}], 'totalPages': 1},
            {'appId': p.APP_ID}, 2, draft, {'content': []}, {'content': [{'type': kind} for kind in ('SCREENSHOT', 'TABLET_SCREENSHOT') for _ in range(8)]}, draft,
            {'content': [{'versionId': 2, 'publishType': 'INSTANTLY', 'partialValue': -1}]}, None]
        args = types.SimpleNamespace(metadata=Path('.'), images=Path('.'), apk=Path(__file__), version_code=35, resume_version=2)
        with patch.object(p, 'API', return_value=api), patch.object(p, 'metadata', return_value=payload), patch.object(p, 'prepare_images', return_value=[]), patch.object(Path, 'write_text'):
            p.publish(args)
        api.upload.assert_not_called()
        self.assertTrue(api.request.call_args.args[1].endswith('/2/commit'))

    def test_wrong_app_rejected_before_mutation(self):
        api = unittest.mock.Mock()
        api.request.side_effect = [{'content': [{'versionId': 1, 'versionCode': 26, 'versionStatus': 'ACTIVE'}], 'totalPages': 1}, {'appId': 7}]
        args = types.SimpleNamespace(metadata=Path('.'), images=Path('.'), apk=Path(__file__), version_code=35, resume_version=0)
        with patch.object(p, 'API', return_value=api), patch.object(p, 'metadata', return_value={}), patch.object(p, 'prepare_images', return_value=[]):
            with self.assertRaisesRegex(RuntimeError, 'Unexpected RuStore application ID'):
                p.publish(args)
        self.assertTrue(all(c.args[0] == 'GET' for c in api.request.call_args_list))

if __name__ == '__main__':
    unittest.main()
