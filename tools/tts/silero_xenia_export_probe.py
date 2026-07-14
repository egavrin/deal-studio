#!/usr/bin/env python3
"""Prepare reproducible Silero Xenia fixtures and probe an Android export path."""

import argparse
import hashlib
import json
import math
import os
import shutil
import struct
import time
import wave
from contextlib import redirect_stderr
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as functional
from torch.package import PackageImporter


DEFAULT_MODEL = Path.home() / ".cache/offline-assistant/silero/v5_5_ru.pt"
DEFAULT_OUTPUT = Path("build/tts-export-spike")
SAMPLE_RATE = 48_000
SPEAKER = "xenia"
SPEAKER_ID = 4
FIXTURES = (
    "Поставил таймер на пять минут.",
    "Сейчас в Москве двадцать один градус.",
    "Напоминание создано на завтра, девять часов утра.",
    "Сколько времени потребуется на дорогу?",
    "Я хочу узнать, где находится старый замок.",
    "На двери висит крепкий замок.",
)
ANDROID_BUNDLE_FILES = {
    "predictors.onnx": "silero-v5_5-ru-xenia-predictors.onnx",
    "acoustic.onnx": "silero-v5_5-ru-xenia-acoustic.onnx",
    "window.f32": "silero-v5_5-ru-xenia-window.f32",
    "frontend.json": "silero-v5_5-ru-xenia-frontend.json",
    "accentor.onnx": "silero-v5_5-ru-xenia-accentor.onnx",
    "accentor-ngrams.json": "silero-v5_5-ru-xenia-accentor-ngrams.json",
    "accentor-exceptions.json": "silero-v5_5-ru-xenia-accentor-exceptions.json",
    "homosolver.onnx": "silero-v5_5-ru-xenia-homosolver.onnx",
    "homosolver-vocab.txt": "silero-v5_5-ru-xenia-homosolver-vocab.txt",
    "homographs.json": "silero-v5_5-ru-xenia-homographs.json",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_model(path: Path):
    importer = PackageImporter(str(path))
    package_module = importer.import_module("multi_acc_v3_package")
    root = importer.load_pickle("tts_models", "model")
    package = root.packages[root.speaker_to_package[SPEAKER]]
    quantized_homosolver = {
        "weight": package.accentor.homosolver.model.bert.embeddings.word_embeddings.weight.detach().cpu().clone(),
        "scale": package.accentor.homosolver.model.bert.scale.detach().cpu().clone(),
        "zero_point": package.accentor.homosolver.model.bert.zero_point.detach().cpu().clone(),
    }
    package.unpack_q_model()
    package.q_model_unpacked = True
    return root, package, package_module.classify_text, quantized_homosolver


def frontend_inputs(package, classify_text, text: str) -> dict:
    speaker_ids, _ = package.get_speakers(SPEAKER)
    sentences, _, breaks, rates, pitches, speaker_tensor = package.prepare_tts_model_input(
        text,
        False,
        speaker_ids,
        None,
    )
    sequence, symbol_durations, duration_rate, pitch_coefficients = package.merge_batch_model(
        sentences,
        breaks,
        rates,
        pitches,
        True,
        True,
        True,
        True,
        True,
    )
    sentence_type = package.build_type_ids_inference(
        text,
        classify_text(text),
        sequence.shape[1],
    )
    return {
        "sequence": sequence,
        "speaker_ids": speaker_tensor,
        "symbol_durations": symbol_durations,
        "duration_rate": duration_rate,
        "pitch_coefficients": pitch_coefficients,
        "type_ids": sentence_type,
    }


def write_wav(path: Path, audio: torch.Tensor) -> None:
    samples = np.clip(audio.detach().cpu().numpy(), -1.0, 1.0)
    pcm = (samples * 32767.0).astype(np.int16)
    with wave.open(str(path), "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(SAMPLE_RATE)
        target.writeframes(pcm.tobytes())


def write_istft_fixture(
    path: Path,
    window: torch.Tensor,
    magnitude_logit: torch.Tensor,
    phase: torch.Tensor,
    audio: torch.Tensor,
) -> None:
    window_values = window.detach().cpu().numpy().astype("<f4")
    magnitude_values = magnitude_logit.detach().cpu().numpy().astype("<f4").reshape(-1)
    phase_values = phase.detach().cpu().numpy().astype("<f4").reshape(-1)
    audio_values = audio.detach().cpu().numpy().astype("<f4").reshape(-1)
    frame_count = int(magnitude_logit.shape[2])
    with path.open("wb") as target:
        target.write(b"SILISTF1")
        target.write(struct.pack("<5i", 2400, 600, frame_count, 1201, audio_values.size))
        target.write(window_values.tobytes())
        target.write(magnitude_values.tobytes())
        target.write(phase_values.tobytes())
        target.write(audio_values.tobytes())


def create_fixtures(root, package, classify_text, output_dir: Path) -> list[dict]:
    fixture_dir = output_dir / "reference-wav"
    fixture_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for index, text in enumerate(FIXTURES, start=1):
        inputs = frontend_inputs(package, classify_text, text)
        accented_text = package.accentor(
            text,
            put_stress=True,
            put_stress_homo=True,
            put_yo=True,
            put_yo_homo=True,
            stress_single_vowel=True,
        )
        with torch.no_grad():
            audio = root.apply_tts(
                text=text,
                speaker=SPEAKER,
                sample_rate=SAMPLE_RATE,
                put_accent=True,
                put_stress_homo=True,
                put_yo=True,
                put_yo_homo=True,
            )
        wav_path = fixture_dir / f"{index:02d}.wav"
        write_wav(wav_path, audio)
        rows.append(
            {
                "id": f"xenia-{index:02d}",
                "text": text,
                "accented_text": accented_text,
                "sentence_type_ids": inputs["type_ids"].tolist(),
                "sequence": inputs["sequence"].tolist(),
                "speaker_ids": inputs["speaker_ids"].tolist(),
                "duration_rate": inputs["duration_rate"].tolist(),
                "pitch_coefficients": inputs["pitch_coefficients"].tolist(),
                "sample_rate": SAMPLE_RATE,
                "audio_samples": int(audio.numel()),
                "reference_wav": str(wav_path),
                "reference_sha256": sha256_file(wav_path),
            }
        )
    return rows


def frozen_parameter(value: torch.Tensor) -> torch.nn.Parameter:
    return torch.nn.Parameter(value.detach().clone(), requires_grad=False)


class FrozenEmbedding(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.weight = frozen_parameter(source.weight)

    def forward(self, indices):
        return functional.embedding(indices, self.weight)


class FrozenLinear(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.weight = frozen_parameter(source.weight)
        self.bias = frozen_parameter(source.bias) if source.bias is not None else None

    def forward(self, value):
        return functional.linear(value, self.weight, self.bias)


class FrozenConv1d(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.weight = frozen_parameter(source.weight)
        self.bias = frozen_parameter(source.bias) if source.bias is not None else None
        self.stride = tuple(source.stride)
        self.padding = tuple(source.padding)
        self.dilation = tuple(source.dilation)
        self.groups = int(source.groups)

    def forward(self, value):
        return functional.conv1d(
            value,
            self.weight,
            self.bias,
            self.stride,
            self.padding,
            self.dilation,
            self.groups,
        )


class FrozenLayerNorm(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.weight = frozen_parameter(source.weight)
        self.bias = frozen_parameter(source.bias)
        self.normalized_shape = tuple(source.weight.shape)
        self.eps = float(source.eps)

    def forward(self, value):
        return functional.layer_norm(value, self.normalized_shape, self.weight, self.bias, self.eps)


class EagerSelfAttention(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.input_weight = frozen_parameter(source.in_proj_weight)
        self.input_bias = frozen_parameter(source.in_proj_bias)
        self.output = FrozenLinear(source.out_proj)
        self.num_heads = int(source.num_heads)
        self.head_dim = int(source.embed_dim) // self.num_heads
        self.scale = math.sqrt(float(self.head_dim))

    def forward(self, value):
        batch_size, sequence_length, embedding_size = value.shape
        query, key, projected_value = functional.linear(
            value,
            self.input_weight,
            self.input_bias,
        ).chunk(3, dim=-1)

        def split_heads(tensor):
            return tensor.reshape(batch_size, sequence_length, self.num_heads, self.head_dim).transpose(1, 2)

        query = split_heads(query)
        key = split_heads(key)
        projected_value = split_heads(projected_value)
        weights = functional.softmax(torch.matmul(query, key.transpose(-2, -1)) / self.scale, dim=-1)
        attended = torch.matmul(weights, projected_value)
        attended = attended.transpose(1, 2).reshape(batch_size, sequence_length, embedding_size)
        return self.output(attended)


class EagerFftBlock(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.attention = EagerSelfAttention(source.self_attn)
        self.convolution1 = FrozenConv1d(source.conv1)
        self.convolution2 = FrozenConv1d(source.conv2)
        self.normalization1 = FrozenLayerNorm(source.norm1)
        self.normalization2 = FrozenLayerNorm(source.norm2)
        self.use_linear = bool(source.use_linear)
        self.pitch_predictor = bool(source.pitch_pred)

    def forward(self, value):
        value = self.normalization1(value + self.attention(value))
        convolution_input = value if self.use_linear else value.transpose(1, 2)
        convolution_output = self.convolution2(functional.relu(self.convolution1(convolution_input)))
        if self.pitch_predictor:
            convolution_output = functional.relu(convolution_output)
            convolution_output = convolution_input + convolution_output + convolution_output
        else:
            convolution_output = convolution_input + convolution_output
        if not self.use_linear:
            convolution_output = convolution_output.transpose(1, 2)
        return self.normalization2(convolution_output)


class EagerTransformer(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.position = frozen_parameter(source.pos_encoder.pe)
        self.position_scale = frozen_parameter(source.pos_encoder.scale)
        self.layers = torch.nn.ModuleList(EagerFftBlock(layer) for layer in source.layers.children())
        self.normalization = FrozenLayerNorm(source.norm)

    def forward(self, value):
        sequence_length = value.shape[1]
        value = value.transpose(0, 1)
        value = value + self.position_scale * self.position[:sequence_length]
        value = value.transpose(0, 1)
        for layer in self.layers:
            value = layer(value)
        return self.normalization(value)


class EagerHourglassTransformer(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.position = frozen_parameter(source.pos_encoder.pe)
        self.position_scale = frozen_parameter(source.pos_encoder.scale)
        self.pre_layers = torch.nn.ModuleList(
            EagerFftBlock(layer) for layer in source.pre_vanilla_layers.children()
        )
        self.shortened_layers = torch.nn.ModuleList(
            EagerFftBlock(layer) for layer in source.shorten_layers.children()
        )
        self.post_layers = torch.nn.ModuleList(
            EagerFftBlock(layer) for layer in source.post_vanilla_layers.children()
        )
        self.upsample = FrozenLinear(source.upsample.proj)
        self.normalization = FrozenLayerNorm(source.norm)
        self.shorten_factor = int(source.shorten_factor)
        self.embedding_size = int(source.upsample.dim)

    def forward(self, value):
        original_length = value.shape[1]
        value = value.transpose(0, 1)
        value = value + self.position_scale * self.position[:original_length]
        value = value.transpose(0, 1)
        for layer in self.pre_layers:
            value = layer(value)

        padding = (self.shorten_factor - original_length % self.shorten_factor) % self.shorten_factor
        value = functional.pad(value, (0, 0, 0, padding))
        residual = value
        value = functional.avg_pool1d(
            value.transpose(1, 2),
            kernel_size=self.shorten_factor,
            stride=self.shorten_factor,
        ).transpose(1, 2)
        shortened_input = value
        for layer in self.shortened_layers:
            value = layer(shortened_input)
        value = self.upsample(value).reshape(value.shape[0], -1, self.embedding_size)
        value = (value + residual)[:, :original_length]
        for layer in self.post_layers:
            value = layer(value)
        return self.normalization(value)


class EagerConvNextBlock(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.depthwise = FrozenConv1d(source.dwconv)
        self.normalization = FrozenLayerNorm(source.norm)
        self.pointwise1 = FrozenLinear(source.pwconv1)
        self.pointwise2 = FrozenLinear(source.pwconv2)
        self.gamma = frozen_parameter(source.gamma)

    def forward(self, value):
        residual = value
        value = self.depthwise(value).transpose(1, 2)
        value = self.normalization(value)
        value = self.pointwise2(functional.gelu(self.pointwise1(value)))
        return residual + (self.gamma * value).transpose(1, 2)


class EagerVocoderBackbone(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.embedding = FrozenConv1d(source.embed)
        self.input_normalization = FrozenLayerNorm(source.norm)
        self.blocks = torch.nn.ModuleList(EagerConvNextBlock(block) for block in source.convnext.children())
        self.output_normalization = FrozenLayerNorm(source.final_layer_norm)

    def forward(self, value):
        value = self.embedding(value)
        value = self.input_normalization(value.transpose(1, 2)).transpose(1, 2)
        for block in self.blocks:
            value = block(value)
        return self.output_normalization(value.transpose(1, 2))


class EagerAccentor(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.embedding = frozen_parameter(source.embedding.weight)
        self.stress_layers = torch.nn.ModuleList(
            FrozenLinear(layer) for index, layer in enumerate(source.stress_clf.children()) if index % 2 == 0
        )
        self.yo_layers = torch.nn.ModuleList(
            FrozenLinear(layer) for index, layer in enumerate(source.yo_clf.children()) if index % 2 == 0
        )

    def forward(self, ngram_ids, ngram_mask):
        embedded = functional.embedding(ngram_ids, self.embedding)
        mask = ngram_mask.unsqueeze(-1)
        embedded = torch.sum(embedded * mask, dim=1) / torch.clamp(torch.sum(mask, dim=1), min=1.0)
        stress = embedded
        yo = embedded
        for index, layer in enumerate(self.stress_layers):
            stress = layer(stress)
            if index != len(self.stress_layers) - 1:
                stress = functional.relu(stress)
        for index, layer in enumerate(self.yo_layers):
            yo = layer(yo)
            if index != len(self.yo_layers) - 1:
                yo = functional.relu(yo)
        return stress, yo


class EagerSeriesPredictor(torch.nn.Module):
    def __init__(self, source):
        super().__init__()
        self.embedding = FrozenEmbedding(source.embedding)
        self.speaker_embedding = FrozenEmbedding(source.speaker_embedding)
        self.type_embedding = FrozenEmbedding(source.type_embedding)
        self.transformer = EagerTransformer(source.transformer)
        self.output = FrozenLinear(source.lin)
        self.uses_type = bool(source.utt_type_emb)

    def forward(self, sequence, speaker_ids, type_ids):
        embedded = self.embedding(sequence)
        speaker = self.speaker_embedding(speaker_ids).unsqueeze(1).repeat(1, sequence.shape[1], 1)
        embedded = embedded + speaker
        if self.uses_type:
            embedded = embedded + self.type_embedding(type_ids)
        return self.output(self.transformer(embedded))


class PredictorWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.duration_predictor = EagerSeriesPredictor(model.dur_predictor.dur_pred)
        self.pitch_predictor = EagerSeriesPredictor(model.pitch_predictor.pitch_pred)

    def forward(self, sequence, speaker_ids, duration_rate, pitch_coefficients, type_ids):
        log_duration = self.duration_predictor(sequence, speaker_ids, type_ids).squeeze(-1)
        duration = torch.round(torch.clamp(torch.exp(log_duration) - 1.0, min=0.0) / duration_rate)
        duration = torch.cat(
            (
                torch.clamp(duration[:, :1], max=5.0),
                duration[:, 1:-3],
                torch.clamp(duration[:, -3:-2], max=13.0),
                torch.full_like(duration[:, -2:-1], 13.0),
                torch.clamp(duration[:, -1:], max=7.0),
            ),
            dim=1,
        )

        pitch = self.pitch_predictor(sequence, speaker_ids, type_ids).transpose(1, 2)
        pitch = torch.where(torch.abs(pitch) < 0.001, torch.zeros_like(pitch), pitch)
        coefficients = pitch_coefficients.unsqueeze(1)
        mean_pitch = torch.full_like(coefficients, 4.240881961191882)
        adjusted_pitch = pitch * coefficients + (coefficients - 1.0) * mean_pitch
        adjusted_pitch = torch.where(pitch * coefficients == 0, torch.zeros_like(adjusted_pitch), adjusted_pitch)
        punctuation_mask = torch.zeros_like(sequence, dtype=torch.bool)
        for symbol_id in (3, 5, 6, 7, 8, 9, 10, 44, 45):
            punctuation_mask = punctuation_mask | (sequence == symbol_id)
        adjusted_pitch = adjusted_pitch.masked_fill(punctuation_mask.unsqueeze(1), 0.0)
        return duration, adjusted_pitch


class AcousticNetworkWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.embedding = FrozenEmbedding(model.tacotron.embedding)
        self.speaker_embedding = FrozenEmbedding(model.tacotron.speaker_embedding)
        self.encoder = EagerTransformer(model.tacotron.encoder)
        self.pitch_projection = FrozenConv1d(model.tacotron.pitch_proj)
        self.decoder = EagerHourglassTransformer(model.tacotron.decoder)
        self.mel_projection = FrozenLinear(model.tacotron.lin)
        self.vocoder_backbone = EagerVocoderBackbone(model.vocoder.backbone)
        self.vocoder_projection = FrozenLinear(model.vocoder.head.out)
        self.pitch_strength = float(model.tacotron.pitch_strength)

    def forward(self, sequence, speaker_ids, pitch, alignment):
        embedded = self.embedding(sequence)
        speaker = self.speaker_embedding(speaker_ids).unsqueeze(1).repeat(1, sequence.shape[1], 1)
        encoded = self.encoder(embedded) + speaker
        encoded = encoded + self.pitch_projection(pitch).transpose(1, 2) * self.pitch_strength
        expanded = torch.bmm(alignment.transpose(1, 2), encoded)
        decoded = self.decoder(expanded)
        mel = self.mel_projection(decoded).transpose(1, 2)
        projected = self.vocoder_projection(self.vocoder_backbone(mel)).transpose(1, 2)
        return torch.chunk(projected, 2, dim=1)


def duration_alignment(duration: torch.Tensor) -> torch.Tensor:
    token_count = duration.shape[1]
    identity = torch.eye(token_count, dtype=torch.float32)
    expanded = torch.repeat_interleave(identity, duration[0].to(torch.long), dim=0)
    return expanded.transpose(0, 1).unsqueeze(0)


def accentor_inputs(words: list[str], ngrams: dict[str, int]) -> tuple[torch.Tensor, torch.Tensor]:
    rows = []
    unknown = ngrams["UNK"]
    for word in words:
        wrapped = f"<{word}>"
        ids = [
            ngrams[wrapped[start:start + size]]
            for size in range(1, len(word) + 4)
            for start in range(0, len(wrapped) - size + 1)
            if wrapped[start:start + size] in ngrams
        ]
        if len(word) < 1 and word in ngrams:
            ids.append(ngrams[word])
        rows.append(ids or [unknown])
    width = max(map(len, rows))
    ids = torch.zeros((len(rows), width), dtype=torch.long)
    mask = torch.zeros((len(rows), width), dtype=torch.float32)
    for row, values in enumerate(rows):
        ids[row, :len(values)] = torch.tensor(values, dtype=torch.long)
        mask[row, :len(values)] = 1.0
    return ids, mask


def export_frontend_bundle(package, quantized_homosolver: dict, output_dir: Path) -> dict:
    frontend = package.accentor
    accentor = frontend.accentor
    ngrams = dict(accentor.model.embedding.ngram_dict)
    metadata_target = output_dir / "silero-v5_5-ru-xenia-frontend.json"
    metadata_target.write_text(
        json.dumps(
            {
                "symbols": package.symbols,
                "symbol_to_id": package.symbol_to_id,
                "alphabet": package.alphabet,
                "sos_token": package.sos_token,
                "eos_token": package.eos_token,
                "speaker_id": SPEAKER_ID,
                "type_to_id": {"st": 0, "wh_q": 1, "general_q": 2, "alternative_q": 3, "tag_q": 4, "exclam": 5},
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    ngrams_target = output_dir / "silero-v5_5-ru-xenia-accentor-ngrams.json"
    ngrams_target.write_text(json.dumps(ngrams, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    exceptions_target = output_dir / "silero-v5_5-ru-xenia-accentor-exceptions.json"
    exceptions_target.write_text(
        json.dumps(accentor.exceptions, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    homographs_target = output_dir / "silero-v5_5-ru-xenia-homographs.json"
    homographs_target.write_text(
        json.dumps(frontend.homosolver.homodict, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    vocab_target = output_dir / "silero-v5_5-ru-xenia-homosolver-vocab.txt"
    vocabulary = frontend.homosolver.tokenizer.vocab
    vocab_target.write_text(
        "\n".join(token for token, _ in sorted(vocabulary.items(), key=lambda item: item[1])) + "\n",
        encoding="utf-8",
    )
    tokenizer_fixtures_target = output_dir / "silero-v5_5-ru-xenia-homosolver-tokenizer-fixtures.json"
    tokenizer_fixture_texts = (
        "На двери висит крепкий [HOMO] замок [/HOMO] .",
        "Я хочу узнать, где находится старый [HOMO] замок [/HOMO] .",
        "Поставил таймер на пять минут.",
        "Сколько времени потребуется на дорогу?",
    )
    tokenizer_fixtures_target.write_text(
        json.dumps(
            [
                {"text": text, "ids": frontend.homosolver.tokenizer(text)}
                for text in tokenizer_fixture_texts
            ],
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )

    sample_words = ["поставил", "таймер", "москве", "замок", "духовку"]
    ids, mask = accentor_inputs(sample_words, ngrams)
    eager = EagerAccentor(accentor.model).eval()
    target = output_dir / "silero-v5_5-ru-xenia-accentor.onnx"
    export_onnx_module(
        eager,
        (ids, mask),
        target,
        ["ngram_ids", "ngram_mask"],
        ["stress_logits", "yo_logits"],
        dynamic_axes={
            "ngram_ids": {0: "word_count", 1: "ngram_count"},
            "ngram_mask": {0: "word_count", 1: "ngram_count"},
            "stress_logits": {0: "word_count"},
            "yo_logits": {0: "word_count"},
        },
    )
    import onnxruntime
    session = onnxruntime.InferenceSession(str(target))
    ort_stress, ort_yo = session.run(
        None,
        {"ngram_ids": ids.numpy(), "ngram_mask": mask.numpy()},
    )
    with torch.no_grad():
        eager_stress, eager_yo = eager(ids, mask)
        source_stress, source_yo = accentor.model(sample_words)
    source_stress_predictions = torch.argmax(source_stress, dim=1)
    source_yo_predictions = torch.argmax(source_yo, dim=1)

    homosolver = frontend.homosolver
    marked_sentences = []
    for sentence in ("На двери висит крепкий замок.", "Я хочу узнать, где находится старый замок."):
        marked_sentences.append(next(row[4] for row in homosolver._find_and_tag_homos(sentence) if row[4] is not None))
    token_rows = [torch.tensor(homosolver.tokenizer(sentence), dtype=torch.long) for sentence in marked_sentences]
    input_ids = torch.nn.utils.rnn.pad_sequence(
        token_rows,
        batch_first=True,
        padding_value=homosolver.tokenizer.pad_token_id,
    )
    homo_starts = torch.stack(
        [torch.where(row == homosolver.tokenizer.homo_start_id)[0][0] for row in token_rows]
    )
    homo_ends = torch.stack(
        [torch.where(row == homosolver.tokenizer.homo_end_id)[0][0] for row in token_rows]
    )
    homosolver_float_target = output_dir / "silero-v5_5-ru-xenia-homosolver-float.onnx"
    homosolver_target = output_dir / "silero-v5_5-ru-xenia-homosolver.onnx"
    torch.onnx.export(
        homosolver.model,
        (input_ids, homo_starts, homo_ends),
        str(homosolver_float_target),
        input_names=["input_ids", "homo_start_ids", "homo_end_ids"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "homo_start_ids": {0: "batch"},
            "homo_end_ids": {0: "batch"},
            "logits": {0: "batch"},
        },
        opset_version=17,
        dynamo=False,
    )
    replace_homosolver_embedding_with_int8(
        homosolver_float_target,
        homosolver_target,
        quantized_homosolver,
    )
    homosolver_float_target.unlink()
    homosolver_session = onnxruntime.InferenceSession(str(homosolver_target))
    ort_homosolver = homosolver_session.run(
        None,
        {
            "input_ids": input_ids.numpy(),
            "homo_start_ids": homo_starts.numpy(),
            "homo_end_ids": homo_ends.numpy(),
        },
    )[0]
    with torch.no_grad():
        source_homosolver = homosolver.model(input_ids, homo_starts, homo_ends).numpy()

    linguistic_fixtures = []
    fixture_texts = list(FIXTURES) + [
        "Что-то новое произошло сегодня?",
        "Всё уже решено.",
        "Этот замок закрыт, а старый замок стоит на холме.",
    ]
    for text in fixture_texts:
        tagged = [row for row in homosolver._find_and_tag_homos(text) if row[4] is not None]
        if tagged:
            homo_token_rows = [torch.tensor(homosolver.tokenizer(row[4]), dtype=torch.long) for row in tagged]
            homo_input = torch.nn.utils.rnn.pad_sequence(
                homo_token_rows,
                batch_first=True,
                padding_value=homosolver.tokenizer.pad_token_id,
            )
            homo_start = torch.stack([
                torch.where(row == homosolver.tokenizer.homo_start_id)[0][0] for row in homo_token_rows
            ])
            homo_end = torch.stack([
                torch.where(row == homosolver.tokenizer.homo_end_id)[0][0] for row in homo_token_rows
            ])
            homo_logits = homosolver_session.run(
                None,
                {
                    "input_ids": homo_input.numpy(),
                    "homo_start_ids": homo_start.numpy(),
                    "homo_end_ids": homo_end.numpy(),
                },
            )[0].reshape(-1)
        else:
            homo_input = torch.empty((0, 0), dtype=torch.long)
            homo_start = torch.empty((0,), dtype=torch.long)
            homo_end = torch.empty((0,), dtype=torch.long)
            homo_logits = np.empty((0,), dtype=np.float32)

        resolved_text = homosolver(text)
        _, clean_tokens, _ = accentor._tokenize(resolved_text)
        accent_ids, accent_mask = accentor_inputs(clean_tokens, ngrams)
        stress_logits, yo_logits = session.run(
            None,
            {"ngram_ids": accent_ids.numpy(), "ngram_mask": accent_mask.numpy()},
        )
        linguistic_fixtures.append(
            {
                "text": text,
                "homograph_input_ids": homo_input.numpy().tolist(),
                "homograph_starts": homo_start.numpy().tolist(),
                "homograph_ends": homo_end.numpy().tolist(),
                "homograph_logits": homo_logits.tolist(),
                "resolved_text": resolved_text,
                "accent_ngram_ids": accent_ids.numpy().tolist(),
                "accent_ngram_mask": accent_mask.numpy().tolist(),
                "stress_logits": stress_logits.tolist(),
                "yo_logits": yo_logits.tolist(),
                "accented_text": accentor(resolved_text),
            }
        )
    linguistic_fixtures_target = output_dir / "silero-v5_5-ru-xenia-linguistic-fixtures.json"
    linguistic_fixtures_target.write_text(
        json.dumps(linguistic_fixtures, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    return {
        "accentor": {"path": str(target), "bytes": target.stat().st_size},
        "metadata": str(metadata_target),
        "ngrams": {"path": str(ngrams_target), "count": len(ngrams)},
        "exceptions": {"path": str(exceptions_target), "count": len(accentor.exceptions)},
        "homographs": {"path": str(homographs_target), "count": len(frontend.homosolver.homodict)},
        "homosolver_vocab": {"path": str(vocab_target), "count": len(vocabulary)},
        "homosolver_tokenizer_fixtures": str(tokenizer_fixtures_target),
        "homosolver": {"path": str(homosolver_target), "bytes": homosolver_target.stat().st_size},
        "linguistic_fixtures": str(linguistic_fixtures_target),
        "parity": {
            "source_stress_max_absolute_difference": float(torch.max(torch.abs(eager_stress - source_stress))),
            "source_yo_max_absolute_difference": float(torch.max(torch.abs(eager_yo - source_yo))),
            "runtime_stress_max_absolute_difference": float(np.max(np.abs(ort_stress - eager_stress.numpy()))),
            "runtime_yo_max_absolute_difference": float(np.max(np.abs(ort_yo - eager_yo.numpy()))),
            "source_stress_predictions_match": bool(
                np.array_equal(np.argmax(ort_stress, axis=1), source_stress_predictions.numpy())
            ),
            "source_yo_predictions_match": bool(
                np.array_equal(np.argmax(ort_yo, axis=1), source_yo_predictions.numpy())
            ),
            "homosolver_max_absolute_difference": float(np.max(np.abs(ort_homosolver - source_homosolver))),
        },
    }


def replace_homosolver_embedding_with_int8(source: Path, target: Path, quantized: dict) -> None:
    import onnx
    from onnx import TensorProto, helper, numpy_helper

    model = onnx.load(str(source))
    embedding_name = "bert.embeddings.word_embeddings.weight"
    embedding = next(value for value in model.graph.initializer if value.name == embedding_name)
    model.graph.initializer.remove(embedding)
    model.graph.initializer.append(
        numpy_helper.from_array(quantized["weight"].numpy().astype(np.int8), embedding_name)
    )
    scale_name = "homosolver_embedding_scale"
    zero_name = "homosolver_embedding_zero_point"
    model.graph.initializer.append(
        numpy_helper.from_array(quantized["scale"].numpy().astype(np.float32).reshape(()), scale_name)
    )
    model.graph.initializer.append(
        numpy_helper.from_array(quantized["zero_point"].numpy().astype(np.float32).reshape(()), zero_name)
    )
    for index, node in enumerate(model.graph.node):
        if node.op_type != "Gather" or node.input[0] != embedding_name:
            continue
        float_output = node.output[0]
        quantized_output = f"{float_output}_int8"
        cast_output = f"{float_output}_float"
        centered_output = f"{float_output}_centered"
        node.output[0] = quantized_output
        model.graph.node.insert(
            index + 1,
            helper.make_node("Cast", [quantized_output], [cast_output], to=TensorProto.FLOAT),
        )
        model.graph.node.insert(
            index + 2,
            helper.make_node("Sub", [cast_output, zero_name], [centered_output]),
        )
        model.graph.node.insert(
            index + 3,
            helper.make_node("Mul", [centered_output, scale_name], [float_output]),
        )
        break
    else:
        raise RuntimeError("Could not find homosolver word embedding Gather")
    onnx.checker.check_model(model)
    onnx.save(model, str(target))


def publish_android_bundle(output_dir: Path, publish_dir: Path) -> dict:
    publish_dir.mkdir(parents=True, exist_ok=True)
    files = []
    for target_name, source_name in ANDROID_BUNDLE_FILES.items():
        source = output_dir / source_name
        if not source.is_file():
            raise FileNotFoundError(f"Missing exported bundle file: {source}")
        target = publish_dir / target_name
        shutil.copy2(source, target)
        files.append(
            {
                "name": target_name,
                "bytes": target.stat().st_size,
                "sha256": sha256_file(target),
            }
        )
    manifest = {
        "id": "silero-v5_5-ru-xenia-android-onnx",
        "source_sha256": "50081637b602126ee06cb3bc8a744d25651d2da149ee8864b9a379bfdd934437",
        "speaker": SPEAKER,
        "speaker_id": SPEAKER_ID,
        "sample_rate": SAMPLE_RATE,
        "license": "CC-BY-NC",
        "files": files,
        "total_bytes": sum(file["bytes"] for file in files),
    }
    manifest_target = publish_dir / "manifest.json"
    manifest_target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"path": str(publish_dir), "manifest": str(manifest_target), **manifest}


def specialize_torchscript_guards(graph) -> None:
    guarded_operations = {"prim::is_nested", "aten::is_autocast_enabled"}

    def visit(block) -> None:
        for node in list(block.nodes()):
            for child in node.blocks():
                visit(child)
            if node.kind() not in guarded_operations:
                continue
            constant = graph.create("prim::Constant")
            constant.i_("value", 0)
            constant.output().setType(torch._C.BoolType.get())
            constant.insertBefore(node)
            node.output().replaceAllUsesWith(constant.output())
            node.destroy()

    visit(graph.block())
    torch._C._jit_pass_constant_propagation(graph)
    torch._C._jit_pass_dce(graph)
    torch._C._jit_pass_lint(graph)


def export_onnx_module(module, args, target: Path, input_names, output_names, dynamic_axes) -> None:
    traced = torch.jit.trace(module.eval(), args, check_trace=False, strict=False)
    frozen = torch.jit.freeze(traced.eval())
    specialize_torchscript_guards(frozen.graph)
    torch.onnx.export(
        frozen,
        args,
        str(target),
        input_names=input_names,
        output_names=output_names,
        dynamic_axes=dynamic_axes,
        opset_version=17,
        dynamo=False,
    )


def probe_onnx_runtime(main_model, predictor, acoustic, fixtures, predictor_target, acoustic_target) -> dict:
    import onnxruntime

    options = onnxruntime.SessionOptions()
    options.intra_op_num_threads = max(1, min(4, (os.cpu_count() or 1)))
    predictor_session = onnxruntime.InferenceSession(str(predictor_target), options)
    acoustic_session = onnxruntime.InferenceSession(str(acoustic_target), options)
    rows = []
    for fixture in fixtures:
        predictor_inputs = {
            "sequence": np.asarray(fixture["sequence"], dtype=np.int64),
            "speaker_ids": np.asarray(fixture["speaker_ids"], dtype=np.int64),
            "duration_rate": np.asarray(fixture["duration_rate"], dtype=np.float32),
            "pitch_coefficients": np.asarray(fixture["pitch_coefficients"], dtype=np.float32),
            "type_ids": np.asarray(fixture["sentence_type_ids"], dtype=np.int64),
        }
        started = time.perf_counter()
        ort_duration, ort_pitch = predictor_session.run(None, predictor_inputs)
        predictor_ms = (time.perf_counter() - started) * 1000.0
        token_count = ort_duration.shape[1]
        alignment = np.repeat(
            np.eye(token_count, dtype=np.float32),
            ort_duration[0].astype(np.int64),
            axis=0,
        ).T[None, :, :]
        acoustic_inputs = {
            "sequence": predictor_inputs["sequence"],
            "speaker_ids": predictor_inputs["speaker_ids"],
            "pitch": ort_pitch,
            "alignment": alignment,
        }
        started = time.perf_counter()
        ort_magnitude, ort_phase = acoustic_session.run(None, acoustic_inputs)
        acoustic_ms = (time.perf_counter() - started) * 1000.0

        torch_args = tuple(torch.from_numpy(predictor_inputs[name]) for name in (
            "sequence",
            "speaker_ids",
            "duration_rate",
            "pitch_coefficients",
            "type_ids",
        ))
        with torch.no_grad():
            torch_duration, torch_pitch = predictor(*torch_args)
            torch_alignment = duration_alignment(torch_duration)
            torch_magnitude, torch_phase = acoustic(
                torch_args[0],
                torch_args[1],
                torch_pitch,
                torch_alignment,
            )
            spectrum = torch.clamp(torch.exp(torch.from_numpy(ort_magnitude)), max=100.0) * torch.complex(
                torch.cos(torch.from_numpy(ort_phase)),
                torch.sin(torch.from_numpy(ort_phase)),
            )
            ort_audio = main_model.vocoder.head.istft(spectrum).unsqueeze(1)[0]
            reference_audio, _ = main_model(
                torch_args[0], torch_args[1], SAMPLE_RATE, {}, torch_args[2], torch_args[3],
                None, None, "cpu", -1, False, torch_args[4], None,
            )
        audio_difference = torch.abs(ort_audio - reference_audio)
        rows.append(
            {
                "id": fixture["id"],
                "text_length": int(predictor_inputs["sequence"].shape[1]),
                "audio_frames": int(alignment.shape[2]),
                "audio_samples": int(ort_audio.numel()),
                "duration_exact": bool(np.array_equal(ort_duration, torch_duration.numpy())),
                "pitch_max_absolute_difference": float(np.max(np.abs(ort_pitch - torch_pitch.numpy()))),
                "magnitude_max_absolute_difference": float(
                    np.max(np.abs(ort_magnitude - torch_magnitude.numpy()))
                ),
                "phase_max_absolute_difference": float(np.max(np.abs(ort_phase - torch_phase.numpy()))),
                "pcm_max_absolute_difference": float(torch.max(audio_difference)),
                "pcm_mean_absolute_difference": float(torch.mean(audio_difference)),
                "predictor_ms": predictor_ms,
                "acoustic_ms": acoustic_ms,
            }
        )
    return {
        "onnxruntime_version": onnxruntime.__version__,
        "all_durations_exact": all(row["duration_exact"] for row in rows),
        "max_pcm_absolute_difference": max(row["pcm_max_absolute_difference"] for row in rows),
        "fixtures": rows,
    }


def probe_onnx(package, fixtures: list[dict], output_dir: Path) -> dict:
    fixture = fixtures[0]
    main_model = package.models[package.speaker_to_model[SPEAKER]].eval()
    predictor = PredictorWrapper(main_model).eval()
    predictor_args = (
        torch.tensor(fixture["sequence"], dtype=torch.long),
        torch.tensor(fixture["speaker_ids"], dtype=torch.long),
        torch.tensor(fixture["duration_rate"], dtype=torch.float32),
        torch.tensor(fixture["pitch_coefficients"], dtype=torch.float32),
        torch.tensor(fixture["sentence_type_ids"], dtype=torch.long),
    )
    with torch.no_grad():
        duration, pitch = predictor(*predictor_args)
    alignment = duration_alignment(duration)
    acoustic = AcousticNetworkWrapper(main_model).eval()
    acoustic_args = (predictor_args[0], predictor_args[1], pitch, alignment)
    predictor_target = output_dir / "silero-v5_5-ru-xenia-predictors.onnx"
    acoustic_target = output_dir / "silero-v5_5-ru-xenia-acoustic.onnx"
    log_path = output_dir / "onnx-export.log"
    with torch.no_grad():
        magnitude_logit, phase = acoustic(*acoustic_args)
        spectrum = torch.clamp(torch.exp(magnitude_logit), max=100.0) * torch.complex(
            torch.cos(phase),
            torch.sin(phase),
        )
        split_audio = main_model.vocoder.head.istft(spectrum).unsqueeze(1)[0]
        reference_audio, _ = main_model(
            predictor_args[0], predictor_args[1], SAMPLE_RATE, {}, predictor_args[2], predictor_args[3],
            None, None, "cpu", -1, False, predictor_args[4], None,
        )
        difference = torch.abs(split_audio - reference_audio)
        parity = {
            "max_absolute_difference": float(torch.max(difference)),
            "mean_absolute_difference": float(torch.mean(difference)),
            "audio_samples": int(reference_audio.numel()),
        }
    window_target = output_dir / "silero-v5_5-ru-xenia-window.f32"
    main_model.vocoder.head.istft.window.detach().cpu().numpy().astype("<f4").tofile(window_target)
    istft_fixture_target = output_dir / "silero-v5_5-ru-xenia-istft-reference.bin"
    write_istft_fixture(
        istft_fixture_target,
        main_model.vocoder.head.istft.window,
        magnitude_logit,
        phase,
        split_audio,
    )
    try:
        with log_path.open("w", encoding="utf-8") as log, redirect_stderr(log):
            export_onnx_module(
                predictor,
                predictor_args,
                predictor_target,
                ["sequence", "speaker_ids", "duration_rate", "pitch_coefficients", "type_ids"],
                ["duration", "pitch"],
                dynamic_axes={
                    "sequence": {1: "text_length"},
                    "duration_rate": {1: "text_length"},
                    "pitch_coefficients": {1: "text_length"},
                    "type_ids": {1: "text_length"},
                    "duration": {1: "text_length"},
                    "pitch": {2: "text_length"},
                },
            )
            export_onnx_module(
                acoustic,
                acoustic_args,
                acoustic_target,
                ["sequence", "speaker_ids", "pitch", "alignment"],
                ["magnitude_logit", "phase"],
                dynamic_axes={
                    "sequence": {1: "text_length"},
                    "pitch": {2: "text_length"},
                    "alignment": {1: "text_length", 2: "audio_frames"},
                    "magnitude_logit": {2: "audio_frames"},
                    "phase": {2: "audio_frames"},
                },
            )
        return {
            "status": "exported",
            "predictors": {"path": str(predictor_target), "bytes": predictor_target.stat().st_size},
            "acoustic": {"path": str(acoustic_target), "bytes": acoustic_target.stat().st_size},
            "istft": {
                "fft_size": 2400,
                "hop_length": 600,
                "padding": "same",
                "window": str(window_target),
                "reference_fixture": str(istft_fixture_target),
            },
            "parity": parity,
            "runtime_parity": probe_onnx_runtime(
                main_model,
                predictor,
                acoustic,
                fixtures,
                predictor_target,
                acoustic_target,
            ),
        }
    except Exception as error:
        predictor_target.unlink(missing_ok=True)
        acoustic_target.unlink(missing_ok=True)
        with log_path.open("a", encoding="utf-8") as log:
            log.write(f"\n{type(error).__name__}: {error}\n")
        return {
            "status": "blocked",
            "reason": f"{type(error).__name__}: {error}",
            "log": str(log_path),
            "parity": parity,
            "next_probe": "Reconstruct remaining TorchScript attention modules as eager PyTorch if freezing cannot remove control flow.",
        }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--probe-onnx", action="store_true")
    parser.add_argument("--require-export", action="store_true")
    parser.add_argument("--publish-dir", type=Path)
    args = parser.parse_args()

    if not args.model.is_file():
        parser.error(f"Silero package not found: {args.model}")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    root, package, classify_text, quantized_homosolver = load_model(args.model)
    fixtures = create_fixtures(root, package, classify_text, args.output_dir)
    fixture_path = args.output_dir / "frontend-fixtures.json"
    fixture_path.write_text(json.dumps(fixtures, ensure_ascii=False, indent=2), encoding="utf-8")

    report = {
        "model": "silero-v5_5-ru",
        "speaker": SPEAKER,
        "speaker_id": SPEAKER_ID,
        "sample_rate": SAMPLE_RATE,
        "source_path": str(args.model),
        "source_bytes": args.model.stat().st_size,
        "source_sha256": sha256_file(args.model),
        "fixtures": str(fixture_path),
        "fixture_count": len(fixtures),
        "onnx": {"status": "not_probed"},
        "frontend": {"status": "not_exported"},
    }
    if args.probe_onnx:
        report["onnx"] = probe_onnx(package, fixtures, args.output_dir)
        report["frontend"] = {
            "status": "exported",
            **export_frontend_bundle(package, quantized_homosolver, args.output_dir),
        }
    if args.publish_dir is not None:
        if not args.probe_onnx:
            parser.error("--publish-dir requires --probe-onnx")
        report["android_bundle"] = publish_android_bundle(args.output_dir, args.publish_dir)

    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 2 if args.require_export and report["onnx"]["status"] != "exported" else 0


if __name__ == "__main__":
    raise SystemExit(main())
