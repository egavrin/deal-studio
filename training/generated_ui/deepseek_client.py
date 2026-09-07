"""Host-only DeepSeek Responses API client for UI dataset generation."""

from __future__ import annotations

import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Mapping, Optional

from jsonschema import Draft202012Validator


DEEPSEEK_RESPONSES_URL = "https://api.deepseek.com/responses"


class DeepSeekGenerationError(RuntimeError):
    def __init__(
        self,
        message: str,
        usage: Optional[Mapping[str, Any]] = None,
        candidate: Optional[Mapping[str, Any]] = None,
    ):
        super().__init__(message)
        self.usage = dict(usage or {})
        self.candidate = dict(candidate) if candidate is not None else None


@dataclass(frozen=True)
class GeneratedBlueprint:
    blueprint: Dict[str, Any]
    provenance: Dict[str, Any]
    usage: Dict[str, Any]


def _canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _extract_output_text(response: Mapping[str, Any]) -> str:
    direct = response.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct
    parts = []
    for item in response.get("output", []):
        if not isinstance(item, dict):
            continue
        for content in item.get("content", []):
            if not isinstance(content, dict):
                continue
            if content.get("type") != "output_text":
                continue
            text = content.get("text") or content.get("output_text")
            if isinstance(text, str):
                parts.append(text)
    if not parts:
        raise DeepSeekGenerationError("DeepSeek response contained no output text")
    return "".join(parts)


class DeepSeekUiTeacher:
    def __init__(
        self,
        *,
        api_key: Optional[str] = None,
        model: str = "deepseek-v4-flash",
        cache_dir: Path,
        timeout_seconds: int = 120,
        reasoning_effort: str = "none",
    ) -> None:
        self.api_key = api_key or os.environ.get("DEEPSEEK_API_KEY")
        if not self.api_key:
            raise DeepSeekGenerationError(
                "DEEPSEEK_API_KEY is not set; the checked-in fixture pipeline remains available"
            )
        self.model = model
        self.cache_dir = cache_dir
        self.timeout_seconds = timeout_seconds
        self.reasoning_effort = reasoning_effort

    def generate(
        self,
        *,
        task: Mapping[str, Any],
        instructions: str,
        schema: Mapping[str, Any],
        retry_feedback: Optional[str] = None,
        retry_candidate: Optional[Mapping[str, Any]] = None,
        reasoning_effort: Optional[str] = None,
    ) -> GeneratedBlueprint:
        active_reasoning_effort = reasoning_effort or self.reasoning_effort
        input_payload: Dict[str, Any] = {"task": dict(task)}
        if retry_feedback:
            input_payload["validator_feedback"] = retry_feedback[:3000]
            if retry_candidate is not None:
                input_payload["previous_candidate"] = dict(retry_candidate)
            input_payload["retry_rule"] = (
                "Return the complete corrected object. Preserve valid parts of the previous "
                "candidate and correct every reported validation dimension."
            )
        request_payload = {
            "model": self.model,
            "instructions": instructions,
            "input": _canonical_json(input_payload),
            "max_output_tokens": 7000,
            "temperature": 0.2,
            "reasoning": {"effort": active_reasoning_effort},
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": "ui_blueprint_v1",
                    "strict": True,
                    "schema": schema,
                }
            },
        }
        request_json = _canonical_json(request_payload)
        request_hash = _sha256_text(request_json)
        cache_path = self.cache_dir / f"{request_hash}.json"
        if cache_path.is_file():
            response = json.loads(cache_path.read_text(encoding="utf-8"))
            cache_hit = True
        else:
            request = urllib.request.Request(
                DEEPSEEK_RESPONSES_URL,
                data=request_json.encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            started = time.monotonic()
            try:
                with urllib.request.urlopen(request, timeout=self.timeout_seconds) as result:
                    response = json.loads(result.read().decode("utf-8"))
            except urllib.error.HTTPError as error:
                body = error.read().decode("utf-8", errors="replace")[:1000]
                raise DeepSeekGenerationError(
                    f"DeepSeek HTTP {error.code}: {body}"
                ) from error
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
                raise DeepSeekGenerationError(f"DeepSeek request failed: {error}") from error
            response["_host_elapsed_ms"] = round((time.monotonic() - started) * 1000)
            self.cache_dir.mkdir(parents=True, exist_ok=True)
            cache_path.write_text(
                json.dumps(response, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            cache_hit = False

        usage = dict(response.get("usage", {}))
        try:
            output_text = _extract_output_text(response)
        except DeepSeekGenerationError as error:
            error.usage = usage
            raise
        transport_normalization = None
        try:
            blueprint = json.loads(output_text)
        except json.JSONDecodeError as error:
            fenced = re.fullmatch(r"\s*```json\s*\n([\s\S]*?)\n```\s*", output_text)
            if fenced is None:
                raise DeepSeekGenerationError(
                    f"DeepSeek structured output was not JSON: {error}", usage
                ) from error
            try:
                blueprint = json.loads(fenced.group(1))
            except json.JSONDecodeError as fenced_error:
                raise DeepSeekGenerationError(
                    f"DeepSeek fenced structured output was not JSON: {fenced_error}", usage
                ) from fenced_error
            transport_normalization = "single_json_fence_removed"
        schema_errors = sorted(
            Draft202012Validator(schema).iter_errors(blueprint),
            key=lambda item: list(item.path),
        )
        if schema_errors:
            summary = "; ".join(
                f"{'.'.join(map(str, item.path)) or '$'}: {item.message}"
                for item in schema_errors[:12]
            )
            raise DeepSeekGenerationError(
                f"DeepSeek output violated teacher schema: {summary}", usage, blueprint
            )
        response_hash = _sha256_text(output_text)
        provenance = {
            "generator": self.model,
            "reported_model": response.get("model"),
            "system_fingerprint": response.get("system_fingerprint"),
            "prompt_version": "deepseek-ui-teacher-v1",
            "reasoning_effort": active_reasoning_effort,
            "request_sha256": request_hash,
            "response_sha256": response_hash,
            "cache_hit": cache_hit,
            "response_id": response.get("id"),
            "transport_normalization": transport_normalization,
            "host_elapsed_ms": response.get("_host_elapsed_ms"),
        }
        return GeneratedBlueprint(
            blueprint=blueprint,
            provenance=provenance,
            usage=usage,
        )
