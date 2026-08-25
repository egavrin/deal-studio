#!/usr/bin/env python3
"""Runs exact production prompts against local MLX generated-app adapters."""

from __future__ import annotations

import argparse
import json
import time
from dataclasses import asdict, dataclass
from pathlib import Path

from mlx_lm import load, stream_generate

from build_demo_dataset import DEAL_SYSTEM, PROFILE_SELECTION_CONTRACT, REQUEST_TEMPLATE


GEMMA_PROMPT = """<start_of_turn>user
task=compact_widget_plan
intent=compose_widget
request={request}
layouts=column,row,stack,grid2,section
layout_props=gap:none|xs|sm|md|lg;align:start|center|end|stretch;padding:none|sm|md|lg;section.tone:plain|soft|accent|dark
required=text.heading(title=$title),text.status(status=$status),control.button(onPrimary=$onPrimary,primaryLabel=$primaryLabel)
interaction=surface.app(grid:tiles|outline|neon,gap:none|xs|sm|md|lg,frame:none|soft|bordered,ratio:scene|square|wide)
optional=text.label(text=$title|$status|$primaryLabel),decor.divider,decor.spacer
component_props=heading.style:display|title|compact;status.style:body|badge|caption;button.variant:filled|tonal|outline;button.icon:none|restart|play;tone:default|muted|primary|positive|warning|inverse;align:start|center|end
rules=compose_new_tree;required_once;surface_app_once;bindings_exact;max_16_components;max_depth_6
screen=compact
<end_of_turn>
<start_of_turn>model
"""

QWEN_PROMPT = """<|im_start|>system
{system}<|im_end|>
<|im_start|>user
{request}<|im_end|>
<|im_start|>assistant
"""

REQUESTS = {
    "tic_tac_toe": "Build a polished two-player tic-tac-toe game with reset and win detection",
    "pong": "Build a responsive touch-controlled Pong game with score and restart",
    "pong_dark": "Build a responsive touch-controlled Pong game. Use a dark framed presentation.",
    "arkanoid": "Build an Arkanoid game with bricks, lives, touch paddle control, and restart",
    "tank_duel": "Build a small touch-controlled tank duel with a moving enemy and projectiles",
    "tank_accent": "Build a small touch-controlled tank duel. Use an accent header and an overlay HUD.",
    "runner": "Build a tap-to-jump endless runner with moving obstacles, score, and restart",
    "memory_board": "Build a memory matching board with an accent header and compact controls",
    "particle_playground": "Build a realtime particle playground with a dark HUD, draggable objects, and reset",
}


@dataclass(frozen=True)
class Measurement:
    case: str
    model: str
    adapter: str
    output_file: str
    first_token_ms: int
    total_ms: int
    prompt_tokens: int
    generated_tokens: int
    prompt_tokens_per_second: float
    generated_tokens_per_second: float
    peak_memory_gb: float
    finish_reason: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=("deal", "ui"), required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-tokens", type=int)
    parser.add_argument("--case", action="append", choices=tuple(REQUESTS), dest="cases")
    return parser.parse_args()


def prompt_for(kind: str, request: str) -> str:
    request = request.replace("\n", " ").replace("\r", " ")
    if kind == "ui":
        return GEMMA_PROMPT.format(request=request[:180])
    user = REQUEST_TEMPLATE.format(
        request=request[:240],
        contract=PROFILE_SELECTION_CONTRACT,
    )
    return QWEN_PROMPT.format(system=DEAL_SYSTEM, request=user)


def generate_case(
    model,
    tokenizer,
    prompt: str,
    max_tokens: int,
) -> tuple[str, dict]:
    started = time.perf_counter()
    first_token_at: float | None = None
    chunks: list[str] = []
    final = None
    for response in stream_generate(model, tokenizer, prompt, max_tokens=max_tokens):
        if response.text and first_token_at is None:
            first_token_at = time.perf_counter()
        chunks.append(response.text)
        final = response
    completed = time.perf_counter()
    if final is None:
        raise RuntimeError("Model returned no generation response")
    return "".join(chunks), {
        "first_token_ms": round(((first_token_at or completed) - started) * 1000),
        "total_ms": round((completed - started) * 1000),
        "prompt_tokens": final.prompt_tokens,
        "generated_tokens": final.generation_tokens,
        "prompt_tokens_per_second": round(final.prompt_tps, 2),
        "generated_tokens_per_second": round(final.generation_tps, 2),
        "peak_memory_gb": round(final.peak_memory, 3),
        "finish_reason": final.finish_reason,
    }


def main() -> int:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    model, tokenizer = load(args.model, adapter_path=str(args.adapter))
    extension = "deal" if args.kind == "deal" else "uidsl"
    max_tokens = args.max_tokens or (1536 if args.kind == "deal" else 512)
    measurements: list[Measurement] = []
    selected_cases = args.cases or list(REQUESTS)
    for case in selected_cases:
        request = REQUESTS[case]
        output, metrics = generate_case(
            model,
            tokenizer,
            prompt_for(args.kind, request),
            max_tokens,
        )
        output_path = args.output / f"{case}.{extension}"
        output_path.write_text(output, encoding="utf-8")
        measurements.append(
            Measurement(
                case=case,
                model=args.model,
                adapter=str(args.adapter),
                output_file=str(output_path),
                **metrics,
            )
        )
        print(json.dumps(asdict(measurements[-1]), sort_keys=True))
    report_path = args.output / "metrics.json"
    report_path.write_text(
        json.dumps([asdict(item) for item in measurements], indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
