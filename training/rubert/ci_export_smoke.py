#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
import transformers
from transformers import BertConfig, BertModel

from train_export import (
    INTENTS,
    SLOT_LABELS,
    JointIntentSlotModel,
    create_onnx_session,
    export_joint_onnx,
    run_onnx_contract,
)


def sample_inputs(batch_size, sequence_length, vocab_size):
    input_ids = torch.arange(batch_size * sequence_length, dtype=torch.long).reshape(batch_size, sequence_length)
    input_ids = (input_ids % (vocab_size - 5)) + 5
    attention_mask = torch.ones_like(input_ids)
    attention_mask[:, -1] = 0
    token_type_ids = torch.zeros_like(input_ids)
    return {
        "input_ids": input_ids,
        "attention_mask": attention_mask,
        "token_type_ids": token_type_ids,
    }


def verify_parity(model, session, sample):
    with torch.no_grad():
        torch_intent, torch_slots = model(**sample)
    ort_intent, ort_slots = run_onnx_contract(session, sample)
    np.testing.assert_allclose(torch_intent.detach().cpu().numpy(), ort_intent, rtol=1e-4, atol=1e-5)
    np.testing.assert_allclose(torch_slots.detach().cpu().numpy(), ort_slots, rtol=1e-4, atol=1e-5)
    return {
        "input_shape": list(sample["input_ids"].shape),
        "intent_shape": list(ort_intent.shape),
        "slot_shape": list(ort_slots.shape),
    }


def run(output_dir):
    torch.manual_seed(7)
    config = BertConfig(
        vocab_size=97,
        hidden_size=32,
        num_hidden_layers=1,
        num_attention_heads=4,
        intermediate_size=64,
        max_position_embeddings=64,
        type_vocab_size=2,
    )
    model = JointIntentSlotModel(encoder=BertModel(config)).eval()
    export_sample = sample_inputs(batch_size=1, sequence_length=8, vocab_size=config.vocab_size)

    output_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = output_dir / "rubert-export-contract.onnx"
    export_joint_onnx(model, export_sample, onnx_path)
    onnx.checker.check_model(str(onnx_path))

    session = create_onnx_session(onnx_path)
    cases = [
        verify_parity(model, session, export_sample),
        verify_parity(model, session, sample_inputs(2, 5, config.vocab_size)),
    ]
    report = {
        "torch_version": torch.__version__,
        "transformers_version": transformers.__version__,
        "onnx_version": onnx.__version__,
        "onnxruntime_version": ort.__version__,
        "inputs": [value.name for value in session.get_inputs()],
        "outputs": [value.name for value in session.get_outputs()],
        "intent_labels": len(INTENTS),
        "slot_labels": len(SLOT_LABELS),
        "cases": cases,
    }
    report_path = output_dir / "contract.json"
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, sort_keys=True))


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("build/rubert-ci"))
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    run(args.output_dir)
