"""The emergency guide classifier's features and model, shared by export.py and its checks.

Hashed character 3-5-grams and word 1-2-grams of the lowercased message, multinomial logistic regression over the guide
ids plus 'none', trained by plain SGD with a fixed seed. Pure Python and deterministic, so the same data always gives the
same weights, and the arithmetic is simple to repeat in Kotlin (app/.../emergency/EmergencyClassifier.kt).
"""
import collections, glob, json, math, os, pathlib, random, re

DATA = pathlib.Path(__file__).resolve().parent / 'data'
DIM = 1 << int(os.environ.get('FA_DIM_BITS', '15'))


def fnv1a(s):
    # 32-bit FNV-1a over UTF-8: the same number in Python and Kotlin, unlike Python's own hash().
    h = 0x811c9dc5
    for byte in s.encode('utf-8'):
        h = ((h ^ byte) * 0x01000193) & 0xffffffff
    return h


def features(text):
    t = ' ' + re.sub(r'\s+', ' ', text.lower()).strip() + ' '
    out = collections.Counter()
    for n in (3, 4, 5):
        for i in range(len(t) - n + 1):
            out[fnv1a('c' + t[i:i + n]) % DIM] += 1
    words = re.findall(r"[a-z0-9']+", t)
    for w in words:
        out[fnv1a('w' + w) % DIM] += 1
    for a, b in zip(words, words[1:]):
        out[fnv1a('b' + a + ' ' + b) % DIM] += 1
    norm = math.sqrt(sum(v * v for v in out.values())) or 1.0
    return {k: v / norm for k, v in out.items()}


class Model:
    def __init__(self, labels):
        self.labels = sorted(labels)
        self.w = {y: collections.defaultdict(float) for y in self.labels}
        self.b = {y: 0.0 for y in self.labels}

    def scores(self, x):
        s = {y: self.b[y] + sum(self.w[y].get(k, 0.0) * v for k, v in x.items()) for y in self.labels}
        m = max(s.values())
        e = {y: math.exp(v - m) for y, v in s.items()}
        z = sum(e.values())
        return {y: v / z for y, v in e.items()}

    def fit(self, data, epochs=8, lr=0.5, l2=1e-5, seed=7):
        rnd = random.Random(seed)
        xs = [(features(q), y) for q, y in data]
        for _ in range(epochs):
            rnd.shuffle(xs)
            for x, y in xs:
                p = self.scores(x)
                for c in self.labels:
                    g = p[c] - (1.0 if c == y else 0.0)
                    if abs(g) < 1e-4:
                        continue
                    wc = self.w[c]
                    for k, v in x.items():
                        wc[k] -= lr * (g * v + l2 * wc[k])
                    self.b[c] -= lr * g
            lr *= 0.8
        return self

    def predict(self, q):
        p = self.scores(features(q))
        y = max(p, key=p.get)
        return y, p[y]


def training_rows():
    """Messages written for each guide and look-alikes that need none (data/train), then every labelled set (labeller
    a, or the single labeller of sets 58-60), each question once."""
    rows = []
    for f in sorted(glob.glob(str(DATA / 'train' / 'gen-*.jsonl'))):
        rows += [(r['q'], r['label']) for r in map(json.loads, open(f)) if r]
    seen = set()
    for r in map(json.loads, open(DATA / 'labelled.jsonl')):
        if r['labeller'] in 'ax' and (r['q'], r['expected']) not in seen:
            rows.append((r['q'], r['expected'])); seen.add((r['q'], r['expected']))
    return rows
