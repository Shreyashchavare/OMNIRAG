"""
verify_rs256_bridge.py
CLI bridge for Direction A: Verifying Java-generated RS256 assertions using Python Agent and RAG auth verifiers.
Usage:
    py -3.10 verify_rs256_bridge.py <fixture_path>
"""
import sys
import os
import json
from pathlib import Path
from cryptography.hazmat.primitives.serialization import load_pem_public_key
from cryptography.hazmat.backends import default_backend

# Add services to sys.path
root_dir = Path(__file__).resolve().parents[3]
agent_service_path = root_dir / "ai-services" / "agent-service"
rag_service_path = root_dir / "ai-services" / "rag-service"

if str(agent_service_path) not in sys.path:
    sys.path.insert(0, str(agent_service_path))
if str(rag_service_path) not in sys.path:
    sys.path.insert(0, str(rag_service_path))

# Mock external optional deps if needed
for mod_name in ["langchain_ollama", "httpx"]:
    if mod_name not in sys.modules:
        from unittest.mock import MagicMock
        m = MagicMock()
        if mod_name == "httpx":
            m.HTTPError = Exception
            m.HTTPStatusError = Exception
        sys.modules[mod_name] = m

import importlib.util

# Load agent_service auth_service
from app.services.auth_service import (
    InMemoryGatewayAssertionReplayService,
    set_cached_public_key as agent_set_cached_key,
    set_default_replay_service as agent_set_default_replay,
    verify_gateway_assertion as agent_verify,
)

# Load rag_service auth_service
rag_auth_path = rag_service_path / "app" / "services" / "auth_service.py"
spec = importlib.util.spec_from_file_location("rag_auth_service", str(rag_auth_path))
rag_auth_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rag_auth_module)
rag_set_cached_key = rag_auth_module.set_cached_public_key
rag_set_default_replay = rag_auth_module.set_default_replay_service
rag_verify = rag_auth_module.verify_gateway_assertion


def main():
    if len(sys.argv) < 2:
        print("Usage: py -3.10 verify_rs256_bridge.py <fixture_path>", file=sys.stderr)
        sys.exit(2)

    fixture_path = sys.argv[1]
    if not os.path.exists(fixture_path):
        print(f"Fixture file not found: {fixture_path}", file=sys.stderr)
        sys.exit(2)

    with open(fixture_path, "r", encoding="utf-8") as f:
        fixtures = json.load(f)

    public_key_pem = fixtures["public_key_pem"].encode("utf-8")
    public_key = load_pem_public_key(public_key_pem, backend=default_backend())

    agent_set_cached_key(public_key)
    rag_set_cached_key(public_key)

    agent_set_default_replay(InMemoryGatewayAssertionReplayService())
    rag_set_default_replay(rag_auth_module.InMemoryGatewayAssertionReplayService())

    expected_sub = fixtures["expected_sub"]
    expected_roles = fixtures.get("expected_roles", "ROLE_USER,ROLE_ADMIN")
    expected_rid = fixtures["expected_rid"]
    valid_token = fixtures["valid_token"]

    services = [
        ("agent-service", agent_verify, fixtures.get("valid_token_agent", valid_token), fixtures.get("valid_token_rag")),
        ("rag-service", rag_verify, fixtures.get("valid_token_rag", valid_token), fixtures.get("valid_token_agent")),
    ]

    for service_name, verifier_fn, own_valid_token, other_valid_token in services:
        prefix = service_name.split("-")[0]
        exp_mth = fixtures.get(f"expected_method_{prefix}", "POST")
        exp_pth = fixtures.get(f"expected_path_{prefix}", "/api/v1/agent/chat" if service_name == "agent-service" else "/api/v1/rag/search")

        # 1. Verify valid token for this service
        try:
            claims = verifier_fn(own_valid_token, expected_audience=service_name, expected_method=exp_mth, expected_path=exp_pth)
            assert claims["sub"] == expected_sub, f"sub mismatch: {claims['sub']} != {expected_sub}"
            assert claims["rid"] == expected_rid, f"rid mismatch: {claims['rid']} != {expected_rid}"
            assert claims["aud"] == service_name, f"aud mismatch: {claims['aud']} != {service_name}"
            assert claims["mth"] == exp_mth, f"mth mismatch: {claims['mth']} != {exp_mth}"
            assert claims["pth"] == exp_pth, f"pth mismatch: {claims['pth']} != {exp_pth}"
            if "roles" in claims:
                assert expected_roles in claims["roles"] or claims["roles"] == expected_roles, f"roles mismatch: {claims['roles']} != {expected_roles}"
        except Exception as e:
            print(f"FAILED: {service_name} valid token verification rejected: {e}", file=sys.stderr)
            sys.exit(1)

        # Request binding method mismatch rejection check
        try:
            verifier_fn(own_valid_token, expected_audience=service_name, expected_method="DELETE", expected_path=exp_pth)
            print(f"FAILED: {service_name} accepted token with mismatched method DELETE", file=sys.stderr)
            sys.exit(1)
        except Exception:
            pass

        # Request binding path mismatch rejection check
        try:
            verifier_fn(own_valid_token, expected_audience=service_name, expected_method=exp_mth, expected_path="/api/v1/other")
            print(f"FAILED: {service_name} accepted token with mismatched path /api/v1/other", file=sys.stderr)
            sys.exit(1)
        except Exception:
            pass

        # Cross-audience rejection check: other service's token must be rejected
        if other_valid_token:
            cross_rejected = False
            try:
                verifier_fn(other_valid_token, expected_audience=service_name, expected_method=exp_mth, expected_path=exp_pth)
            except Exception:
                cross_rejected = True
            if not cross_rejected:
                print(f"FAILED: {service_name} accepted other service's token (audience isolation broken)", file=sys.stderr)
                sys.exit(1)

        # 2. Verify negative cases
        negative_cases = [
            "tampered_signature_token",
            "tampered_payload_token",
            "wrong_key_token",
            "wrong_issuer_token",
            "expired_token",
            "future_token",
            "invalid_uuid_token",
            "missing_rid_token",
            "missing_jti_token",
            "missing_typ_token",
            "invalid_typ_token",
            "alg_hs256_token",
            "alg_none_token",
            "wrong_audience_token",
            "array_audience_token",
            "missing_audience_token",
            "padded_audience_token",
            "wrong_method_token",
            "wrong_path_token",
            "missing_method_token",
            "missing_path_token",
            "whitespace_method_token",
            "whitespace_path_token",
            "case_mismatched_path_token",
        ]

        for case_name in negative_cases:
            if case_name not in fixtures:
                continue
            token = fixtures[case_name]
            rejected = False
            try:
                verifier_fn(token, expected_audience=service_name, expected_method=exp_mth, expected_path=exp_pth)
            except Exception:
                rejected = True

            if not rejected:
                print(f"FAILED: {service_name} accepted invalid token for case: {case_name}", file=sys.stderr)
                sys.exit(1)

    print("SUCCESS: All Java-generated RS256 assertions verified successfully across Agent and RAG services.")
    sys.exit(0)


if __name__ == "__main__":
    main()
