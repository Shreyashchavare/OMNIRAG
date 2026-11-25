"""
Direction B Cross-Language RS256 Interoperability Test Suite.
Generates ephemeral RS256 assertions using Python PyJWT and verifies them against
Java Knowledge Service and Processing Service GatewayAssertionVerifier implementations.
"""

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
import jwt

root_dir = Path(__file__).resolve().parents[3]
VALID_SUB = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
ROLES = "ROLE_USER,ROLE_ADMIN"
ISSUER = "gateway-service"
RID = "req-cross-rs256-py-to-java"


class TestPythonToJavaRs256Interop(unittest.TestCase):

    def _export_public_key_pem(self, key: rsa.RSAPublicKey) -> str:
        pem_bytes = key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        return pem_bytes.decode("utf-8")

    def _build_fixture_data(self):
        test_private_key = rsa.generate_private_key(65537, 2048)
        rogue_private_key = rsa.generate_private_key(65537, 2048)
        test_public_key = test_private_key.public_key()

        now = int(time.time())

        # Valid token
        valid_payload = {
            "sub": VALID_SUB,
            "iss": ISSUER,
            "aud": "knowledge-service",
            "iat": now,
            "exp": now + 3600,
            "rid": RID,
            "roles": ROLES,
            "jti": str(uuid.uuid4()),
            "mth": "GET",
            "pth": "/api/v1/documents",
        }
        valid_token = jwt.encode(valid_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        valid_payload_processing = dict(valid_payload)
        valid_payload_processing["aud"] = "processing-service"
        valid_payload_processing["jti"] = str(uuid.uuid4())
        valid_token_processing = jwt.encode(valid_payload_processing, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        # Audience negative cases
        wrong_aud_payload = dict(valid_payload)
        wrong_aud_payload["aud"] = "wrong-service"
        wrong_audience_token = jwt.encode(wrong_aud_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        missing_aud_payload = dict(valid_payload)
        del missing_aud_payload["aud"]
        missing_audience_token = jwt.encode(missing_aud_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        array_aud_payload = dict(valid_payload)
        array_aud_payload["aud"] = ["knowledge-service", "processing-service"]
        array_audience_token = jwt.encode(array_aud_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        # Method & path negative cases
        wrong_mth_payload = dict(valid_payload)
        wrong_mth_payload["mth"] = "DELETE"
        wrong_method_token = jwt.encode(wrong_mth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        wrong_pth_payload = dict(valid_payload)
        wrong_pth_payload["pth"] = "/api/v1/wrong"
        wrong_path_token = jwt.encode(wrong_pth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        missing_mth_payload = dict(valid_payload)
        del missing_mth_payload["mth"]
        missing_method_token = jwt.encode(missing_mth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        missing_pth_payload = dict(valid_payload)
        del missing_pth_payload["pth"]
        missing_path_token = jwt.encode(missing_pth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        whitespace_mth_payload = dict(valid_payload)
        whitespace_mth_payload["mth"] = " GET "
        whitespace_method_token = jwt.encode(whitespace_mth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        whitespace_pth_payload = dict(valid_payload)
        whitespace_pth_payload["pth"] = " /api/v1/documents "
        whitespace_path_token = jwt.encode(whitespace_pth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        case_pth_payload = dict(valid_payload)
        case_pth_payload["pth"] = "/API/V1/DOCUMENTS"
        case_mismatched_path_token = jwt.encode(case_pth_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        # Negative tokens
        parts = valid_token.split(".")
        tampered_sig_token = f"{parts[0]}.{parts[1]}.{parts[2][:-4]}AAAA"

        tampered_payload = dict(valid_payload)
        tampered_payload["sub"] = str(uuid.uuid4())
        tampered_payload_b64 = base64.urlsafe_b64encode(json.dumps(tampered_payload).encode("utf-8")).decode("utf-8").rstrip("=")
        tampered_payload_token = f"{parts[0]}.{tampered_payload_b64}.{parts[2]}"

        wrong_key_token = jwt.encode(valid_payload, rogue_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        wrong_iss_payload = dict(valid_payload)
        wrong_iss_payload["iss"] = "untrusted-service"
        wrong_issuer_token = jwt.encode(wrong_iss_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        expired_payload = dict(valid_payload)
        expired_payload["iat"] = now - 3600
        expired_payload["exp"] = now - 60
        expired_token = jwt.encode(expired_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        future_payload = dict(valid_payload)
        future_payload["iat"] = now + 3600
        future_payload["exp"] = now + 7200
        future_token = jwt.encode(future_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        invalid_uuid_payload = dict(valid_payload)
        invalid_uuid_payload["sub"] = "not-a-valid-uuid"
        invalid_uuid_token = jwt.encode(invalid_uuid_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        missing_rid_payload = dict(valid_payload)
        del missing_rid_payload["rid"]
        missing_rid_token = jwt.encode(missing_rid_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        # Missing jti
        missing_jti_payload = dict(valid_payload)
        del missing_jti_payload["jti"]
        missing_jti_token = jwt.encode(missing_jti_payload, test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})

        missing_typ_token = jwt.encode(valid_payload, test_private_key, algorithm="RS256", headers={"typ": None})
        invalid_typ_token = jwt.encode(valid_payload, test_private_key, algorithm="RS256", headers={"typ": "INVALID", "alg": "RS256"})

        hs256_header_b64 = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode("utf-8")).decode("utf-8").rstrip("=")
        alg_hs256_token = f"{hs256_header_b64}.{parts[1]}.fakesignaturehmac"

        none_header_b64 = base64.urlsafe_b64encode(json.dumps({"alg": "none", "typ": "JWT"}).encode("utf-8")).decode("utf-8").rstrip("=")
        alg_none_token = f"{none_header_b64}.{parts[1]}."

        return {
            "public_key_pem": self._export_public_key_pem(test_public_key),
            "expected_sub": VALID_SUB,
            "expected_roles": ROLES,
            "expected_rid": RID,
            "expected_iss": ISSUER,
            "valid_token": valid_token,
            "valid_token_knowledge": valid_token,
            "valid_token_processing": valid_token_processing,
            "wrong_audience_token": wrong_audience_token,
            "missing_audience_token": missing_audience_token,
            "array_audience_token": array_audience_token,
            "tampered_signature_token": tampered_sig_token,
            "tampered_payload_token": tampered_payload_token,
            "wrong_key_token": wrong_key_token,
            "wrong_issuer_token": wrong_issuer_token,
            "expired_token": expired_token,
            "future_token": future_token,
            "invalid_uuid_token": invalid_uuid_token,
            "missing_rid_token": missing_rid_token,
            "missing_jti_token": missing_jti_token,
            "missing_typ_token": missing_typ_token,
            "invalid_typ_token": invalid_typ_token,
            "alg_hs256_token": alg_hs256_token,
            "alg_none_token": alg_none_token,
            "expected_method": "GET",
            "expected_path": "/api/v1/documents",
            "wrong_method_token": wrong_method_token,
            "wrong_path_token": wrong_path_token,
            "missing_method_token": missing_method_token,
            "missing_path_token": missing_path_token,
            "whitespace_method_token": whitespace_method_token,
            "whitespace_path_token": whitespace_path_token,
            "case_mismatched_path_token": case_mismatched_path_token,
        }

    def test_python_to_java_knowledge_service_rs256(self):
        fixture_data = self._build_fixture_data()
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
            json.dump(fixture_data, f, indent=2)
            temp_path = f.name

        try:
            ks_dir = root_dir / "backend" / "knowledge-service"
            mvnw_cmd = ks_dir / ("mvnw.cmd" if os.name == "nt" else "mvnw")
            norm_path = Path(temp_path).as_posix()
            cmd = [
                str(mvnw_cmd),
                "test",
                "-Dtest=CrossLanguageRs256JavaBridgeTest",
                f"-Dcross.lang.fixture={norm_path}",
            ]
            result = subprocess.run(
                cmd,
                cwd=str(ks_dir),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=120,
            )
            self.assertEqual(
                result.returncode,
                0,
                f"Knowledge Service RS256 bridge test failed (exit {result.returncode}):\n{result.stdout}\n{result.stderr}",
            )
            self.assertIn("BUILD SUCCESS", result.stdout)
        finally:
            if os.path.exists(temp_path):
                os.unlink(temp_path)

    def test_python_to_java_processing_service_rs256(self):
        fixture_data = self._build_fixture_data()
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
            json.dump(fixture_data, f, indent=2)
            temp_path = f.name

        try:
            ps_dir = root_dir / "backend" / "processing-service"
            mvnw_cmd = ps_dir / ("mvnw.cmd" if os.name == "nt" else "mvnw")
            norm_path = Path(temp_path).as_posix()
            cmd = [
                str(mvnw_cmd),
                "test",
                "-Dtest=CrossLanguageRs256JavaBridgeTest",
                f"-Dcross.lang.fixture={norm_path}",
            ]
            result = subprocess.run(
                cmd,
                cwd=str(ps_dir),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=120,
            )
            self.assertEqual(
                result.returncode,
                0,
                f"Processing Service RS256 bridge test failed (exit {result.returncode}):\n{result.stdout}\n{result.stderr}",
            )
            self.assertIn("BUILD SUCCESS", result.stdout)
        finally:
            if os.path.exists(temp_path):
                os.unlink(temp_path)


if __name__ == "__main__":
    unittest.main()
