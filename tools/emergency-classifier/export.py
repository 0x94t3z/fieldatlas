"""export.py OUT.bin PARITY.json: train the shipped classifier and write it with one byte per weight, plus test
vectors the Kotlin port must reproduce (app/src/test/resources/emergency-classifier-parity.json).

    python3 tools/emergency-classifier/export.py app/src/main/assets/emergency/classifier.bin \\
        app/src/test/resources/emergency-classifier-parity.json

Deterministic: the same data gives the same file (SHA-256 in DATASETS.md).
Format (little-endian): b"FAEC", version u8, dimBits u8, label count u16; each label as u8 length + UTF-8; per label
bias f32 and scale f32; then for each label 2^dimBits signed bytes (weight = byte * scale)."""
import json, math, struct, sys
import clf

model = clf.Model({y for _, y in clf.training_rows()}).fit(clf.training_rows())
bits = clf.DIM.bit_length() - 1
out = bytearray(b'FAEC') + struct.pack('<BBH', 1, bits, len(model.labels))
for y in model.labels:
    e = y.encode(); out += struct.pack('<B', len(e)) + e
quantized = {}
for y in model.labels:
    w = model.w[y]; scale = (max((abs(v) for v in w.values()), default=0.0) / 127.0) or 1e-9
    quantized[y] = (scale, [max(-127, min(127, round(w.get(k, 0.0) / scale))) for k in range(clf.DIM)])
    out += struct.pack('<ff', model.b[y], scale)
for y in model.labels:
    out += struct.pack(f'<{clf.DIM}b', *quantized[y][1])
open(sys.argv[1], 'wb').write(bytes(out))


def predict_quantized(text):
    x = clf.features(text); s = {}
    for y in model.labels:
        scale, ws = quantized[y]; s[y] = model.b[y] + scale * sum(ws[k] * v for k, v in x.items())
    m = max(s.values()); e = {y: math.exp(v - m) for y, v in s.items()}; z = sum(e.values())
    top = max(e, key=e.get); return top, e[top] / z


probe = [json.loads(l)['q'] for l in open(clf.DATA / 'held61' / 'questions.jsonl')][:120]
vectors = [{'q': t, 'label': predict_quantized(t)[0], 'p': round(predict_quantized(t)[1], 5)} for t in probe]
json.dump(vectors, open(sys.argv[2], 'w'), ensure_ascii=False, indent=0)
print(f'{len(out):,} bytes; {len(vectors)} test vectors')
