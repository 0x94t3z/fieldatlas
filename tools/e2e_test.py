#!/usr/bin/env python3
"""
Field Atlas end-to-end answer-quality battery.

Runs each case in e2e_cases.json through a byte-for-byte replica of the phone
pipeline (dual-seed LLM keyword planner -> porter + BM25 retrieval across the
installed packs -> relative-shift merge -> 2048-token evidence prompt) against
a local llama-server running THE SAME quantized model the phone uses, and
grades every answer with a second call to that same model (the LLM judge)
plus deterministic phrase rules from the case file.

Prerequisite (the model must be the one under test):
    llama-server -m /path/to/Qwen3.5-2B-Q4_K_M.gguf -c 8192 -ngl 0 --host 127.0.0.1 --port 8081

Usage:
    python3 tools/e2e_test.py                        # everything, 3 runs each
    python3 tools/e2e_test.py --only first-war,diet-meat-eggs --runs 1
    python3 tools/e2e_test.py --base-url http://192.168.1.89:8081

Exit code 0 = every case passed its runs; 1 = at least one case failed.
Reports land in tools/e2e_reports/<timestamp>.json (full answers, keywords,
sources, judge reasons) for later diffing.
"""
import argparse, json, os, re, sqlite3, sys, time, urllib.request
from datetime import datetime, timezone

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_PACKS = [
    os.path.join(REPO, "fapacks/world-knowledge-biology/content.sqlite"),
    os.path.join(REPO, "fapacks/world-knowledge-crypto/content.sqlite"),
    os.path.join(REPO, "fapacks/world-knowledge-wikipedia-mini/content.sqlite"),
]

# ----------------------------------------------------------------------------- prompts
# Kept in sync with app PromptBuilder.POLICY (answer rules) and the keyword planner.
POLICY = (
    "You are a research assistant that answers questions using the provided evidence.\n\n"
    "Rules:\n"
    "1. Use ONLY the provided evidence.\n"
    "2. If the evidence is insufficient, say you cannot answer confidently.\n"
    "3. Cite sources using [S1], [S2], etc.\n"
    "4. For comparisons, reason step by step.\n"
    "5. Do not invent facts not present in the evidence.\n"
    "6. If the evidence mentions the question's entities but does not actually answer the "
    "question, treat it as insufficient and say you cannot answer confidently. Do not pad an "
    "answer with related-but-irrelevant background.\n"
    "7. When you do have evidence, state only what it directly supports. Attribute every "
    "specific number to its source and the year it refers to, and prefer a direct quote of "
    "the evidence's own wording for any key figure. Do not turn a projection or projection "
    "table into a firmer claim than the evidence states, do not silently substitute a nearby "
    "alternative statistic for the one asked, and when the question asks for a number answer "
    "with the number itself.\n"
    "8. Answer in the same language as the question. Be direct and informative rather than "
    "terse, but never pad the answer with facts about a different entity than the one asked about.\n"
    "9. When the evidence provides the answer, give it fully and directly instead of hedging, "
    "even if you have heard a different figure before; the evidence, not your memory, is the "
    "ground truth here.\n"
    "10. Where evidence gives explicit numbers or direct comparisons, prefer them over relative "
    "statements.\n\n"
)
# Verbatim mirror of QueryExpansion (system persona, user prompt, parser) - if the app's
# planner changes, change this too or the replica stops being a replica.
SYSTEM_PLANNER = (
    "You are a search planner for an offline document retrieval system. You may use general "
    "knowledge of words, synonyms and likely document vocabulary, but never answer a question "
    "and never explain anything. Your only output is the requested keyword line."
)
def planner_prompt(question):
    return ("/no_think\n"
            "You are the search planner for an offline document search engine. Do not answer.\n"
            f"Question: {question.strip()}\n"
            "Write search keywords: first the essential content words of the question, dropping "
            "words like 'tell', 'me', 'about'; then close synonyms and terms that documents "
            "answering it would actually use, in any morphological form; then, if you know from "
            "general knowledge which named articles, species, records or lists documents would cite, "
            "add their exact names as keywords too (for a tallest-animal question add the names of the "
            "candidate species). Aim for at least 8 "
            "distinct keywords - broader coverage beats safe obvious ones - up to 10 total.\n"
            "Reply with exactly one comma-separated line. No sentences, no numbering, no quotes.")

MAX_TERM_CHARS, MAX_TERMS = 48, 10
def parse_keywords(raw):
    cleaned = re.sub(r"</?think>?", " ", raw, flags=re.I)
    terms = []
    for piece in re.split(r"[,;\n]", cleaned):
        t = re.sub(r"^[\d.+\-)*\"'`\s]+", "", piece.strip())
        t = re.sub(r"[.,;:!?)\"'\s]+$", "", t).lower()
        if t and len(t) <= MAX_TERM_CHARS and t.count(" ") <= 2 and t not in terms:
            terms.append(t)
        if len(terms) == MAX_TERMS:
            break
    if terms:
        return terms
    words = [w for w in re.split(r"[^\w]+", cleaned.lower()) if 2 <= len(w) <= MAX_TERM_CHARS]
    return list(dict.fromkeys(words))[:MAX_TERMS]
STOP = {"a","an","and","are","as","at","be","been","being","but","by","can","could","did",
        "do","does","for","from","how","if","in","is","it","its","not","of","on","or","should",
        "that","the","then","these","this","those","to","was","were","what","when","where","who",
        "why","with","would",
        # conversational wrappers, mirroring FtsQuery.STOP_WORDS
        "about","compare","compared","describe","explain","find","give","versus","show","tell",
        "me","my","mine","our","ours","us","please","provide","write","vs"}

# ----------------------------------------------------------------------- porter stemmer
# Classic Porter (1980) - mirrors the app's PorterStemmer.kt closely enough for parity
# work; the packs store porter-stemmed text, so queries must be stemmed the same way.
def _cons(word, i):
    c = word[i]
    if c in "aeiou": return False
    if c == "y": return i == 0 or not _cons(word, i - 1)
    return True
def _measure(word, offset=0):
    cv = "".join("01"[int(_cons(word, i))] for i in range(len(word)))
    return cv.count("10") if cv.find("10") >= 0 else 0
def _m(word, m):  # measure of stem greater than m
    return _measure(word) > m
def _hasv(word): return _measure(word) > 0
def _cvc(word):  # consonant-vowel-consonant with last C not W/X/Y
    if len(word) < 3: return False
    return _cons(word, len(word)-1) and not _cons(word, len(word)-2) and _cons(word, len(word)-3) \
        and word[-1] not in "wxy"
def _r1(word, suf, rep, cond=None):
    if word.endswith(suf):
        stem = word[:-len(suf)]
        if cond is None or cond(stem): return stem + rep
    return None
def _step1a(w):
    for suf, rep in (("sses","ss"),("ies","i")):
        r = _r1(w, suf, rep)
        if r: return r
    if w.endswith("s") and not w.endswith("ss") and len(w) > 2: return w[:-1]
    return w
def _step1b(w):
    if w.endswith("eed"):
        stem_ = w[:-3]
        if _measure(stem_) > 0: return stem_ + "ee"
        return w
    for suf in ("ed","ing"):
        if w.endswith(suf):
            stem = w[:-len(suf)]
            if _hasv(stem):
                if stem.endswith(("at","bl","iz")): return stem + "e"
                if len(stem) >= 2 and stem[-1] == stem[-2] and stem[-1] not in "lsz":
                    return stem[:-1]
                if _measure(stem) == 1 and _cvc(stem): return stem + "e"
                return stem
    return w
_STEP2 = [("ational","ate"),("tional","tion"),("enci","ence"),("anci","ance"),("izer","ize"),
          ("abli","able"),("alli","al"),("entli","ent"),("eli","e"),("ousli","ous"),("ization","ize"),
          ("ation","ate"),("ator","ate"),("alism","al"),("iveness","ive"),("fulness","ful"),
          ("ousness","ous"),("aliti","al"),("iviti","ive"),("biliti","ble"),("logi","log")]
def _step2(w):
    for suf, rep in _STEP2:
        if w.endswith(suf):
            stem = w[:-len(suf)]
            if _m(stem, 0): return stem + rep
    return w
_STEP3 = [("icate","ic"),("ative",""),("alize","al"),("iciti","ic"),("ical","ic"),("ful",""),("ness","")]
def _step3(w):
    for suf, rep in _STEP3:
        if w.endswith(suf):
            stem = w[:-len(suf)]
            if _m(stem, 0): return stem + rep
    return w
_STEP4 = ["al","ance","ence","er","ic","able","ible","ant","ement","ment","ent","ou","ism","ate","iti","ous","ive","ize"]
def _step4(w):
    for suf in _STEP4:
        if w.endswith(suf):
            stem_ = w[:-len(suf)]
            if suf == "ou":
                if _m(stem_, 1) and not stem_.endswith("s"): return stem_
            elif _m(stem_, 1):
                return stem_
    return w
def stem(word):
    if len(word) <= 2: return word
    w = word.lower()
    w = _step1a(w); w = _step1b(w)
    if w.endswith("y") and len(w) > 2 and not _cons(w, len(w)-2): w = w[:-1] + "i"
    w = _step2(w); w = _step3(w); w = _step4(w)
    if w.endswith("e"):
        stem_ = w[:-1]
        if _measure(stem_) > 1 or (_measure(stem_) == 1 and not _cvc(stem_)): w = stem_
    if w.endswith("ss") and len(w) > 4 and w[-3] == "s": w = w[:-1]
    if w.endswith("l") and len(w) > 1 and w[-2] == w[-1] and _measure(w) > 1: w = w[:-1]
    return w

# ------------------------------------------------------------------------------- model
class Model:
    def __init__(self, base_url):
        self.base_url = base_url
    def chat(self, messages, max_tokens, seed=17):
        req = urllib.request.Request(
            self.base_url.rstrip("/") + "/v1/chat/completions",
            data=json.dumps({"messages": messages, "max_tokens": max_tokens, "temperature": 0.3,
                             "seed": seed, "repeat_penalty": 1.1,
                             "chat_template_kwargs": {"enable_thinking": False}}).encode(),
            headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=600) as r:
            d = json.load(r)
        return d["choices"][0]["message"]["content"]

# --------------------------------------------------------------------------- retrieval
class Pack:
    # Mirrors FtsRetriever: title-weighted bm25 (title 3, text 1), candidate pool of
    # conjunction + OR + coverage passes, single selection with doc cap 2, and
    # shortest-title exact-match boosts reserved for the rarest terms.
    BM25 = "bm25(chunks_fts,0.0,0.0,3.0,0.0,1.0)"
    def __init__(self, path):
        self.db = sqlite3.connect(path)
    def terms(self, merged):
        counts, alive = {}, []
        for t in merged:
            c = self.db.execute(f"select count(*) from chunks_fts where chunks_fts match ?", (f'"{t}"',)).fetchone()[0]
            counts[t] = min(c, 100000)
            if c > 0: alive.append(t)
        return counts, alive
    def search(self, expr, limit):
        return self.db.execute(
            f"select chunk_id,document_id,title,source,text,{self.BM25} s from chunks_fts "
            f"where chunks_fts match ? order by s asc, chunk_id asc limit ?", (expr, limit)).fetchall()
    def title_candidate_titles(self, terms):
        expr = " OR ".join(f'title : "{t.replace(chr(34), "")}"' for t in terms[:16])
        if not expr: return []
        return self.db.execute(
            "select title, min(rowid) from chunks_fts where chunks_fts match ? group by title limit 5000",
            (expr,)).fetchall()

    def title_lead(self, rowid):
        return self.db.execute(
            "select chunk_id, document_id, title, source, text, 0.0 from chunks_fts where rowid = ?",
            (rowid,)).fetchone()
    @staticmethod
    def coverage(title, terms):
        tokens = set(re.split(r"[^\w]+", title.lower()))
        return sum(1 for t in terms if t in tokens)
    def retrieve(self, merged, limit=8):
        counts, alive = self.terms(merged)
        pool, considered = {}, set()
        def consider(rows):
            for r in rows:
                k = (r[1], r[0])
                if k not in considered:
                    considered.add(k); pool[k] = r
        # Conjunction ladder, verbatim app shape: AND of the 5 rarest terms, shrinking the
        # group until documents exist; one contribution, then every stage feeds the same pool.
        core = sorted(alive, key=lambda t: counts[t])[:5]
        for size in range(len(core), 1, -1):
            exact = self.search(" AND ".join(f'"{t}"' for t in core[:size]), limit)
            if exact:
                consider(exact)
                break
        consider(self.search(" OR ".join(f'"{t}"' for t in alive), limit * 3))
        if len(pool) < limit:
            per_term = max(2, (limit + 2) // 3)
            for t in alive:
                if len(pool) >= limit * 2: break
                consider(self.search(f'"{t}"', per_term))
        # Title-coverage boosts (mirror of FtsRetriever): titles holding the most query terms
        # win the boost slots; ties go to titles of rarer words (count-sum), then shortest.
        # Case-duplicate pages share a slot; one lede chunk per slot.
        # Title boost (mirror of FtsRetriever): one nomination per term - shortest title
        # containing the term - slots to the rarest terms, cap 3, one lede chunk each.
        candidates = self.title_candidate_titles(alive)
        tokenized = [(title, rowid, set(re.split(r"[^\w]+", title.lower()))) for title, rowid in candidates]
        nominated = []
        for t in alive[:16]:
            hits = [(len(title), title.lower(), rowid) for title, rowid, toks in tokenized if t in toks]
            if hits: nominated.append((counts[t],) + min(hits))
        nominated.sort()
        chosen, seen_lower = [], set()
        for _, _, low, rowid in nominated:
            if low in seen_lower: continue
            seen_lower.add(low); chosen.append(rowid)
            if len(chosen) >= 3: break
        boosts = {}
        for idx, rowid in enumerate(chosen):
            r = self.title_lead(rowid)
            if r is None: continue
            k = (r[1], r[0]); pool[k] = r; boosts[k] = idx
        non_boost = [r[5] for k, r in pool.items() if k not in boosts]
        floor = min(non_boost) if non_boost else 0.0
        ranked = []
        for k, r in pool.items():
            if k in boosts:
                r = list(r); r[5] = floor - (len(chosen) - boosts[k])
            ranked.append(r)
        ranked.sort(key=lambda r: r[5])
        picked, per_doc = [], {}
        for r in ranked:
            if len(picked) >= limit: break
            if per_doc.get(r[1], 0) >= 2: continue
            per_doc[r[1]] = per_doc.get(r[1], 0) + 1
            picked.append(r)
        return picked

def merge(runs, limit=8):
    """Pack-relative merge (mirrors ResearchOrchestrator.mergeEvidence): guaranteed slots by
    pack-internal rank, then order by score shift against each pack's own best hit."""
    runs = [r for r in runs if r]
    seen, selected, shift = set(), [], {}
    quota = min(3, limit)
    for rank in range(min(quota, 2)):
        for run in runs:
            if len(selected) >= limit: break
            if rank >= len(run): continue
            r = run[rank]; k = (r[1], r[0])
            if k not in seen:
                seen.add(k); shift[k] = r[5] - min(x[5] for x in run); selected.append(r)
    if quota >= 3:  # third slots arbitrated by pack-relative strength
        thirds = [(run[2], run[2][5] - min(x[5] for x in run)) for run in runs if len(run) > 2]
        for r, sh in sorted(thirds, key=lambda t: t[1]):
            k = (r[1], r[0])
            if len(selected) < limit and k not in seen:
                seen.add(k); shift[k] = sh; selected.append(r)
    # Fill: GLOBAL shift ordering across all packs (the old pack-sequential fill let the
    # first-listed pack's leftovers claim every remaining slot before the encyclopaedia's
    # rank-3 title article - a title-boosted ally with a tiny relative shift - could speak).
    rest = [(r[5] - min(x[5] for x in run), idx, r)
            for run in runs for idx, r in enumerate(run) if idx >= quota]
    for sh, _, r in sorted(rest, key=lambda t: (t[0], t[1])):
        if len(selected) >= limit: break
        k = (r[1], r[0])
        if k not in seen:
            seen.add(k); shift[k] = sh; selected.append(r)
    return sorted(selected, key=lambda r: shift[(r[1], r[0])])

def build_prompt(question, rows, budget_tokens=2048):
    remaining = budget_tokens * 4 * 65 // 100
    blocks, n = [], 0
    for cid, did, title, source, text, _score in rows:
        if remaining <= 0: break
        n += 1
        prefix = f"[S{n}]\nTitle: {title}\nSource: {source}\nChunk: {did}:x\nText: "
        if len(prefix) >= remaining: break
        body = text[:remaining - len(prefix)]
        blocks.append(prefix + body)
        remaining -= len(prefix) + len(body)
    return ("QUESTION:\n" + question.strip() + "\n\nEVIDENCE:\n" + "\n\n".join(blocks) + "\n\nANSWER:"), n

# ------------------------------------------------------------------------------ judging
REFUSAL_MARKERS = ("cannot answer", "not possible to answer", "not possible", "insufficient",
                   "does not contain", "do not contain", "no source", "unable to answer",
                   "no document", "no information", "does not state", "no evidence")

def rule_check(answer, expect):
    a = " " + answer.lower() + " "
    refused = any(marker in a for marker in REFUSAL_MARKERS)
    if refused and expect.get("refuse_ok", False):
        must_any_skipped = True   # an honest decline satisfies the must-any side for this case
    else:
        must_any_skipped = False
    if not must_any_skipped:
        for group in expect.get("must_any", []):
            if not any(alt.lower() in a for alt in group):
                return False, f"missing every alternative of {group}"
    for phrase in expect.get("forbid", []):
        alts = phrase if isinstance(phrase, list) else [phrase]
        if any(alt.lower() in a for alt in alts):
            return False, f"forbidden phrase present: {alts}"
    return True, ""

def llm_judge(model, case, answer):
    rubric = case["expect"].get("rubric", "Answer is correct, grounded, and not fabricated.")
    refuse_ok = case["expect"].get("refuse_ok", False)
    prompt = (
        "You are grading one answer of an offline RAG assistant. Judge ONLY correctness against "
        "the rubric and the refusal policy. The rubric is the sole authority on what is correct: "
        "if the answer satisfies the rubric it passes, even if you would have answered otherwise. "
        "Do not add facts of your own; check the answer as written.\n"
        f"QUESTION: {case['question']}\nANSWER: {answer.strip()}\nRUBRIC: {rubric}\n"
        f"Refusing to answer (saying it cannot answer confidently) is ACCEPTABLE here: {'yes' if refuse_ok else 'no'}.\n"
        "Reply with exactly two lines:\n"
        "VERDICT: pass or fail\n"
        "REASON: one short sentence that quotes the part of the answer that decides it\n"
    )
    verdicts, reasons, parsed = [], [], 0
    for seed_offset in (0, 7, 13):
        out = model.chat([{"role": "user", "content": prompt}], 72, seed=case_seed(case) + seed_offset)
        m = re.search(r"VERDICT:\s*(pass|fail)", out, re.I)
        if m:
            parsed += 1
            verdicts.append(m.group(1).lower() == "pass")
            rm = re.search(r"REASON:\s*(.+)", out)
            if rm: reasons.append(rm.group(1).strip())
    if not parsed:
        return True, "judge-unparseable (rules already enforced)"
    # Two independent judge samples; a pass from either outweighs a lone hallucinated fail.
    return any(verdicts), "; ".join(reasons[:1])

def case_seed(case):
    return (abs(hash(case["id"])) % 900) + 1

# --------------------------------------------------------------------------------- main
def run_question(model, packs, question, run_seed, stem_terms=False):
    keywords = []
    for seed in (17, 89):
        raw = model.chat([{"role": "system", "content": SYSTEM_PLANNER},
                          {"role": "user", "content": planner_prompt(question)}], 96, seed=seed)
        for t in parse_keywords(raw):
            if t not in keywords:
                keywords.append(t)
        if len(keywords) >= MAX_TERMS: break
    question_terms = []
    for t in re.findall(r"[^\W_]+", question.lower()):
        if len(t) >= 2 and t not in STOP and t not in question_terms: question_terms.append(t)
    # App order: the question's own terms lead, planner synonyms follow (importance signal for
    # coverage order and title-boost tie-breaks).
    terms = list(dict.fromkeys(question_terms + keywords))
    if stem_terms:
        terms = list(dict.fromkeys(terms + [stem(t) for t in terms]))
    rows = merge([p.retrieve(terms) for p in packs])
    prompt, n_sources = build_prompt(question, rows)
    t0 = time.time()
    answer = model.chat([{"role": "system", "content": POLICY}, {"role": "user", "content": prompt}],
                        1536, seed=run_seed)
    return {
        "keywords": keywords, "sources": [[r[2], round(r[5], 1)] for r in rows],
        "answer": answer, "seconds": round(time.time() - t0, 1), "n_sources": n_sources,
    }

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cases", default=os.path.join(REPO, "tools/e2e_cases.json"))
    ap.add_argument("--packs", nargs="*", default=DEFAULT_PACKS)
    ap.add_argument("--base-url", default="http://127.0.0.1:8081")
    ap.add_argument("--runs", type=int, default=0, help="override per-case run count")
    ap.add_argument("--only", default="", help="comma-separated case ids to run")
    ap.add_argument("--stem", action="store_true", help="query with porter-stemmed variants too (experiment)")
    ap.add_argument("--out", default="", help="report path (default tools/e2e_reports/<utc>.json)")
    args = ap.parse_args()

    cases = json.load(open(args.cases))
    if args.only:
        wanted = {s.strip() for s in args.only.split(",")}
        cases = [c for c in cases if c["id"] in wanted]
    if not cases:
        print("no cases matched", file=sys.stderr); sys.exit(2)
    missing = [p for p in args.packs if not os.path.isfile(p)]
    if missing:
        print("missing pack databases (import them or pass --packs):", *missing, sep="\n  ", file=sys.stderr)
        sys.exit(2)
    try:
        urllib.request.urlopen(args.base_url.rstrip("/") + "/health", timeout=5)
    except OSError as e:
        print(f"llama-server not reachable at {args.base_url} ({e}) - see the prerequisite in this file's header", file=sys.stderr)
        sys.exit(2)

    model = Model(args.base_url)
    packs = [Pack(p) for p in args.packs]
    out_path = args.out or os.path.join(REPO, "tools/e2e_reports",
                                        datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + ".json")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    report, failed = [], 0
    for case in cases:
        runs = args.runs or 3
        passed_runs = []
        for run_no in range(1, runs + 1):
            r = run_question(model, packs, case["question"], run_no, stem_terms=args.stem)
            rules_ok, rules_why = rule_check(r["answer"], case["expect"])
            judge_ok, judge_why = llm_judge(model, case, r["answer"])
            r.update(case=case["id"], run=run_no, pass_=rules_ok and judge_ok,
                     fail_reason=rules_why or ("" if judge_ok else f"judge: {judge_why}"))
            passed_runs.append(r)
            print(f"  {case['id']} run {run_no}: {'PASS' if r['pass_'] else 'FAIL'}"
                  + (f" ({r['fail_reason']})" if r["fail_reason"] else f" ({r['seconds']}s, {r['n_sources']} sources)"))
            if not r["pass_"]:
                print("      answer:", r["answer"].replace("\n", " ")[:220])
        need = max(1, (runs * 2 + 2) // 3)          # >= two-thirds of the runs
        ok = sum(1 for r in passed_runs if r["pass_"]) >= need
        failed += 0 if ok else 1
        print(f"{'== PASS' if ok else '== FAIL'}  {case['question']}  "
              f"[{sum(1 for r in passed_runs if r['pass_'])}/{runs}]")
        report.append({"case": case["id"], "question": case["question"], "verdict": "PASS" if ok else "FAIL",
                       "runs": [{k: v for k, v in r.items() if k != "case"} for r in passed_runs]})
        json.dump(report, open(out_path, "w"), indent=1)

    print(f"\n{len(cases) - failed}/{len(cases)} cases passed -> {out_path}")
    sys.exit(1 if failed else 0)

if __name__ == "__main__":
    main()
