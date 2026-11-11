"""export_urgent.py OUT.bin PARITY.json: train the urgency model and write it in the guide classifier's file format,
plus test vectors the Kotlin port must reproduce (app/src/test/resources/emergency-urgent-parity.json).

    python3 tools/emergency-classifier/export_urgent.py app/src/main/assets/emergency/urgent.bin \\
        app/src/test/resources/emergency-urgent-parity.json

Two labels, "urgent" (someone is hurt, ill or in danger now) and "no", over the guide classifier's features
(clf.features). Rows: data/urgent/train.jsonl. Deterministic: the same data gives the same file."""
import json, math, struct, sys
import clf

rows = [json.loads(line) for line in open(clf.DATA / 'urgent' / 'train.jsonl') if line.strip()]
model = clf.Model({'urgent', 'no'}).fit([(r['q'], r['y']) for r in rows])
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


def urgent(text):
    """The probability the shipped (one byte per weight) model gives "urgent"."""
    x = clf.features(text)
    s = {y: model.b[y] + quantized[y][0] * sum(quantized[y][1][k] * v for k, v in x.items()) for y in model.labels}
    m = max(s.values()); e = {y: math.exp(v - m) for y, v in s.items()}
    return e['urgent'] / sum(e.values())


# Every 40th row, so the vectors cover each source of the data.
vectors = [{'q': r['q'], 'p': round(urgent(r['q']), 5)} for r in rows[::40]]
json.dump(vectors, open(sys.argv[2], 'w'), ensure_ascii=False, indent=0)
right = sum((urgent(r['q']) >= 0.5) == (r['y'] == 'urgent') for r in rows)
print(f'{len(out):,} bytes; {len(rows)} rows, {right} read back right at 0.5; {len(vectors)} parity vectors')
