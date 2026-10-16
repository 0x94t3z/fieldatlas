#!/usr/bin/env python3
"""Record every bundled emergency guide in the app's reading voice, so Read aloud plays at once, offline, on any phone.

Input is the read-aloud script the app itself builds (SpeechText.guide), dumped by the GuideSpeechScriptDump test:

  FIELDATLAS_GUIDE_SCRIPT=/tmp/guide-script.json ./gradlew :app:testDebugUnitTest \\
      --tests xyz.fieldatlas.speech.GuideSpeechScriptDump --rerun
  python3 tools/packs/build_guide_audio.py /tmp/guide-script.json --model <sherpa-onnx-supertonic-3-tts-int8 dir>
  python3 tools/packs/build_guide_audio.py --sample --model <same dir>    # the short clip played before download

Each sentence is synthesised with Supertonic 3 (voice F1, the default five steps, normal speed) through sherpa-onnx,
the guide is joined with pauses, encoded as Opus (20 kbps mono) and written to
app/src/main/assets/emergency/audio/<id>.ogg. manifest.json records where each sentence starts, with its text as shown,
so the app can mark the sentence being read. Needs: pip install sherpa-onnx soundfile numpy, and ffmpeg with libopus.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/emergency/audio"
SAMPLE = ROOT / "app/src/main/assets/speech/natural-sample.ogg"
SAMPLE_TEXT = "This is the natural voice. It reads your answers aloud on this phone, even with no signal."
VOICE = {"engine": "Supertonic 3 (int8, sherpa-onnx)", "style": "F1", "steps": 5, "speed": 1.0, "lang": "en"}
PAUSE = 0.45          # between sentences
LONG_PAUSE = 0.8      # after the call line and after a heading
BITRATE = "20k"


def make_tts(model_dir: Path):
    import sherpa_onnx as s
    d = str(model_dir)
    return s.OfflineTts(s.OfflineTtsConfig(model=s.OfflineTtsModelConfig(supertonic=s.OfflineTtsSupertonicModelConfig(
        duration_predictor=f"{d}/duration_predictor.int8.onnx", text_encoder=f"{d}/text_encoder.int8.onnx",
        vector_estimator=f"{d}/vector_estimator.int8.onnx", vocoder=f"{d}/vocoder.int8.onnx", tts_json=f"{d}/tts.json",
        unicode_indexer=f"{d}/unicode_indexer.bin", voice_style=f"{d}/voice.bin"), num_threads=2)))


def encode(wav, rate, target: Path):
    import soundfile as sf
    with tempfile.TemporaryDirectory() as tmp:
        raw = Path(tmp) / "speech.wav"
        sf.write(raw, wav, rate)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(raw), "-ac", "1", "-c:a", "libopus", "-b:a", BITRATE,
                        "-application", "voip", "-map_metadata", "-1", str(target)], check=True)


def generation_config():
    import sherpa_onnx as s
    g = s.GenerationConfig(); g.sid = 0; g.num_steps = VOICE["steps"]; g.speed = VOICE["speed"]; g.extra = {"lang": VOICE["lang"]}
    return g


def render_sample(model_dir: Path):
    """The short clip Library and the voice choice play before the natural voice is downloaded, with where each
    sentence starts (natural-sample.json) so the words can be marked as they are spoken."""
    import numpy as np
    tts = make_tts(model_dir)
    sentences = [part.strip() + "." for part in SAMPLE_TEXT.rstrip(".").split(". ")]
    pieces, marks, at, rate = [], [], 0, None
    for index, sentence in enumerate(sentences):
        audio = tts.generate(sentence, generation_config())
        rate = audio.sample_rate
        samples = np.asarray(audio.samples, dtype=np.float32)
        marks.append({"startMs": round(at * 1000 / rate), "shown": sentence})
        gap = np.zeros(int(PAUSE * rate) if index < len(sentences) - 1 else 0, dtype=np.float32)
        pieces += [samples, gap]
        at += len(samples) + len(gap)
    SAMPLE.parent.mkdir(parents=True, exist_ok=True)
    encode(np.concatenate(pieces), rate, SAMPLE)
    SAMPLE.with_suffix(".json").write_text(json.dumps({"text": SAMPLE_TEXT, "durationMs": round(at * 1000 / rate),
                                                       "segments": marks}, indent=1) + "\n")
    print(f"{SAMPLE.relative_to(ROOT)} {at / rate:.1f}s, {len(marks)} sentences")


def render_guide(args):
    guide, model_dir = args
    import numpy as np
    tts = make_tts(Path(model_dir))
    rate, pieces, marks, at = None, [], [], 0
    for index, segment in enumerate(guide["segments"]):
        audio = tts.generate(segment["spoken"], generation_config())
        rate = audio.sample_rate
        samples = np.asarray(audio.samples, dtype=np.float32)
        marks.append({"startMs": round(at * 1000 / rate), "shown": segment["shown"]})
        heading = not segment["shown"].rstrip().endswith((".", "!", "?", ":"))
        pause = LONG_PAUSE if index == 0 or heading else PAUSE
        pieces += [samples, np.zeros(int(pause * rate), dtype=np.float32)]
        at += len(samples) + int(pause * rate)
    wav = np.concatenate(pieces)
    encode(wav, rate, OUT / f"{guide['id']}.ogg")
    return guide["id"], {"file": f"{guide['id']}.ogg", "durationMs": round(len(wav) * 1000 / rate), "segments": marks}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("script", type=Path, nargs="?", help="JSON written by GuideSpeechScriptDump")
    parser.add_argument("--sample", action="store_true", help="only record the short natural-voice sample")
    parser.add_argument("--model", type=Path, required=True, help="sherpa-onnx-supertonic-3-tts-int8 directory")
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if args.sample:
        return render_sample(args.model)
    if args.script is None:
        parser.error("the guide script is required unless --sample is given")
    script = json.loads(args.script.read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    guides = {}
    with concurrent.futures.ProcessPoolExecutor(args.jobs) as pool:
        for gid, entry in pool.map(render_guide, [(g, str(args.model)) for g in script]):
            guides[gid] = entry
            print(f"{gid:24} {entry['durationMs'] / 1000:6.1f}s  {len(entry['segments'])} sentences", flush=True)
    script_hash = hashlib.sha256(json.dumps(script, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    manifest = {"voice": VOICE, "scriptSha256": script_hash, "guides": dict(sorted(guides.items()))}
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n")
    total = sum(p.stat().st_size for p in OUT.glob("*.ogg"))
    print(f"{len(guides)} guides, {sum(g['durationMs'] for g in guides.values()) / 60000:.1f} min, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
