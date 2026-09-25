# Full end-to-end replica of the Field Atlas research pipeline on the rig:
# keyword planner (dual seed) -> retriever (rarest-conj + OR + per-term, 3 packs)
# -> PromptBuilder packing -> answer via pinned llama-server (Qwen3.5-2B, temp 0.3).
import json, re, sqlite3, sys, time
import requests

SERVER = "http://localhost:8081/v1/chat/completions"
MODEL = "qwen3.5"

SYSTEM_PLANNER = ("You are a search planner for an offline document retrieval system. You may use general "
    "knowledge of words, synonyms and likely document vocabulary, but never answer a question "
    "and never explain anything. Your only output is the requested keyword line.")
POLICY = """/no_think
You are an offline research assistant. Use only the numbered evidence below. Do not use external knowledge. Give a concise answer, compare relevant claims, preserve conflicts and uncertainty, and cite factual claims with [S#] at the end of each claim, for example: Tigers are the largest cats. [S2] Where evidence gives explicit numbers or direct comparisons, prefer them over relative statements. If the evidence does not contain the answer, say that in one sentence and stop - never walk through the sources one by one."""

def planner_prompt(q):
    return ("/no_think\nYou are the search planner for an offline document search engine. Do not answer.\n"
        f"Question: {q.strip()}\n"
        "Write search keywords: first the essential content words of the question, dropping "
        "words like 'tell', 'me', 'about'; then close synonyms and terms that documents "
        "answering it would actually use, in any morphological form. Aim for at least 8 "
        "distinct keywords - broader coverage beats safe obvious ones - up to 10 total.\n"
        "Reply with exactly one comma-separated line. No sentences, no numbering, no quotes.")

def chat(messages, max_tokens, temperature=0.3, seed=None):
    body = {"model": MODEL, "messages": messages, "max_tokens": max_tokens,
            "temperature": temperature, "repeat_penalty": 1.10,
            "chat_template_kwargs": {"enable_thinking": False}}
    if seed is not None: body["seed"] = seed
    r = requests.post(SERVER, json=body, timeout=300)
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]

def parse_keywords(raw):
    cleaned = re.sub(r"</?think>?", " ", raw.split("</think>")[-1], flags=re.I)
    terms = []
    for piece in re.split(r"[;,\n]", cleaned):
        t = re.sub(r"^[\d.+)\-*•\"'`\s]+", "", piece.strip())
        t = re.sub(r"[.,;:!?)\"'\s]+$", "", t).lower()
        if 0 < len(t) <= 48 and t.count(" ") <= 2 and t not in terms:
            terms.append(t)
        if len(terms) == 10: break
    if not terms:
        for w in re.split(r"[^\w]+", cleaned.lower()):
            if 2 <= len(w) <= 48 and w not in terms: terms.append(w)
            if len(terms) == 10: break
    return terms

STOP = set("a an and are as at be been being but by can could did do does for from how if in is it its not of on or should that the these this those to was were what when where who why with would about compare compared describe explain find give versus show tell me my mine our ours us please provide write vs".split())
def question_terms(q):
    out=[]
    for t in re.findall(r"[^\W_]+", q.lower()):
        if len(t)>=2 and t not in STOP and t not in out: out.append(t)
    return out

dbs=[sqlite3.connect(p) for p in (
 "/tmp/porter_pack/content.sqlite",
 "/home/v/fieldatlas/fapacks/world-knowledge-crypto/content.sqlite",
 "/home/v/fieldatlas/fapacks/world-knowledge-wikipedia-mini/content.sqlite")]
def count_capped(db,t): return min(db.execute("select count(*) from chunks_fts where chunks_fts match ?",(f'"{t}"',)).fetchone()[0],2000)
def search(db,expr,limit): return db.execute("select chunk_id,document_id,title,source,text,bm25(chunks_fts,0.0,0.0,3.0,0.0,1.0) s from chunks_fts where chunks_fts match ? order by s asc, chunk_id asc limit ?",(expr,limit)).fetchall()
def retriever(db,merged,limit=8):
    alive=[t for t in merged if count_capped(db,t)>0]
    if not alive: return []
    freq={t:count_capped(db,t) for t in alive}
    core=sorted(alive,key=lambda t:(freq[t],-len(t)))[:5]
    pool={}
    def consider(rows):
        for r in rows:
            k=r[1]+":"+r[0]
            if k not in pool: pool[k]=r
    for size in range(len(core),1,-1):
        exact=search(db," AND ".join(f'"{t}"' for t in core[:size]),limit)
        if not exact: continue
        consider(exact); break
    consider(search(db," OR ".join(f'"{t}"' for t in alive),limit*3))
    if len(pool)<limit:
        per=max(2,(limit+2)//3)
        for t in alive:
            if len(pool)>=limit*2: break
            consider(search(db,f'"{t}"',per))
    titleboost={}
    cands=[]
    for t in alive[:16]:
        c=db.execute("select min(rowid) r from chunks_fts where chunks_fts match ? group by title order by length(title) asc limit 1",(f'title : "{t}"',)).fetchone()
        if c and c[0] is not None:
            rows=db.execute("select chunk_id,document_id,title,source,text,0.0 s from chunks_fts where rowid=?",(c[0],)).fetchall()
            if rows: cands.append((freq[t],rows[0]))
    seenboost=set()
    for f_,r in sorted(cands,key=lambda x:x[0]):
        if len(titleboost)>=3: break
        if r[1] in seenboost: continue
        k=r[1]+":"+r[0]
        seenboost.add(r[1]); pool[k]=r; titleboost[k]=len(titleboost)+1
    floor=min([r[5] for k,r in pool.items() if k not in titleboost] or [0.0])
    ranked=[]
    for k,r in pool.items():
        if k in titleboost:
            r=[r[0],r[1],r[2],r[3],r[4],floor-(3-titleboost[k]+1)]
        ranked.append(r)
    ranked.sort(key=lambda r:r[5])
    picked=[];perdoc={}
    for r in ranked:
        if len(picked)>=limit: break
        if perdoc.get(r[1],0)>=2: continue
        perdoc[r[1]]=perdoc.get(r[1],0)+1
        picked.append(r)
    return picked
def merge(runs, limit=8):
    seen=set(); selected=[]; shift={}
    def key(r): return (r[1],r[0])
    runs=[r for r in runs if r]
    def best(run): return min(r[5] for r in run)
    quota=min(2,limit)
    for rank in range(quota):
        for run in runs:
            if len(selected)>=limit: break
            if rank>=len(run): continue
            r=run[rank]; k=key(r)
            if k not in seen:
                seen.add(k); shift[k]=r[5]-best(run); selected.append(r)
    for run in runs:
        if len(selected)>=limit: break
        for r in run:
            k=key(r)
            if k not in seen:
                seen.add(k); shift[k]=r[5]-best(run); selected.append(r)
                if len(selected)>=limit: break
    return sorted(selected,key=lambda r: shift[key(r)])

def build_prompt(question, rows, budget_tokens=2048):
    remaining = budget_tokens*4*65//100
    blocks=[]; n=0
    for (cid,did,title,source,text,score) in rows:
        if remaining<=0: break
        n+=1
        prefix=f"[S{n}]\nTitle: {title}\nSource: {source}\nChunk: {did}:x\nText: "
        if len(prefix)>=remaining: break
        body=text[:remaining-len(prefix)]
        blocks.append(prefix+body)
        remaining-=len(prefix)+len(body)
    prompt=(POLICY+"\n\nQUESTION:\n"+question.strip()+"\n\nEVIDENCE:\n"
            +"\n\n".join(blocks)+"\n\nANSWER:")
    return prompt, n

def run_question(q, run_no):
    kw=[]
    for seed in (17,89):
        raw=chat([{"role":"system","content":SYSTEM_PLANNER},{"role":"user","content":planner_prompt(q)}],96,seed=seed)
        kw+= [t for t in parse_keywords(raw) if t not in kw]
        if len(kw)>=10: break
    merged=list(dict.fromkeys(kw+question_terms(q)))
    rows=merge([retriever(db,merged) for db in dbs])
    prompt,nsrc=build_prompt(q,rows)
    t0=time.time()
    # mirror the app: system prompt POLICY then packed user content WITHOUT the policy header duplicated:
    answer=chat([{"role":"system","content":POLICY},{"role":"user","content":
        "QUESTION:\n"+q.strip()+"\n\nEVIDENCE:\n"+prompt.split("EVIDENCE:\n",1)[1].rsplit("\n\nANSWER:",1)[0]+"\n\nANSWER:"}],
        1536, seed=run_no)
    return {"q":q,"run":run_no,"keywords":kw,"sources":[(r[2],round(r[5],1)) for r in rows],
            "answer":answer,"secs":round(time.time()-t0,1)}

CASES=json.load(open(sys.argv[1]))
out=[]
for case in CASES:
    for run_no in (1,2,3):
        r=run_question(case["q"], run_no)
        r["expect"]=case.get("expect","")
        out.append(r)
        print(f"### {case['q']} [run {run_no}] {r['secs']}s")
        print("  keywords:", ", ".join(r["keywords"]))
        print("  sources:", "; ".join(f"{t[:38]}({s})" for t,s in r["sources"][:5]))
        print("  ANSWER:", r["answer"].replace("\n"," ")[:400])
        print()
        json.dump(out, open("/tmp/e2e_battery_results.json","w"), indent=1)
print("DONE")
