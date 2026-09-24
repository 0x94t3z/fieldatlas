#!/usr/bin/env python3
"""enwiki dump -> wikiextractor-style <doc> fragments, KEEPING NUMBERS.

The old scratcha/wikiextractor deleted every {{template}}, so every number
rendered by {{convert}}/{{cvt}}/infoboxes vanished ("weigh , and ...").
This extractor expands the number-bearing templates instead.

Usage:
  python3 wiki_extract_numbers.py OUT_DIR [--workers N] [--limit N]

Input: the multistream dump parts already on disk under
/home/v/world_knowledge/generators/archives/wikipedia/archive.
Output: OUT_DIR/shard_00000 ... files of `<doc id=".." url=".." title="..">`
blocks, compatible with content2fapack.iter_wiki_docs.
"""
import bz2
import glob
import html
import os
import re
import sys
from multiprocessing import Pool

ARCHIVE = "/home/v/world_knowledge/generators/archives/wikipedia/archive"
OUT_DOCS_PER_SHARD = 4000

# ---------------------------------------------------------------- templates

NUMBER_TEMPLATES = {
    "convert", "cvt", "Convert", "Cvt",
}
DROP_NAMES = re.compile(
    r"^(ref|cite ?(web|news|book|journal|press|conference)|citation|"
    r"dead ?link|isbn|pmid|pmc|doi|doi|sfn|notetag|efn|note|harv|harvnb|"
    r"main|also|see| further|about|for|other ?uses|redirect|redlink|"
    r"category ?all|portal|commons|commonscat|wikicategory|wpbc|colb|"
    r"flag|flagicon|flagu|flagu|databox|dbq|dsq|as ?of|uses ?mdy|"
    r"lang|langx|transl|IPA|afc|nth|df|use ?dmy|small|lang-nm)$",
    re.I,
)
INFIX_KEEP = re.compile(
    r"^(age|formatnum|e|val|num|convert|cvt|cnote|nota ?bene|plainlist|"
    r"flatlist|unbulleted ?list|nowrap|abbr|convert|date|year|decade|"
    r"start ?date|end ?date|birth ?date|death ?date|age in years)$",
    re.I,
)

def split_template_args(body):
    """Split template body on top-level '|' (ignoring pipes inside [[..]] or {{..}})."""
    if "{{" not in body and "[[" not in body:
        return body.split("|")
    args, depth, cur, inlink = [], 0, [], False
    i = 0
    n = len(body)
    while i < n:
        c = body[i]
        two = body[i:i + 2]
        if two == "{{":
            depth += 1; cur.append(two); i += 2; continue
        if two == "}}":
            depth -= 1
            cur.append(two); i += 2; continue
        if two == "[[":
            inlink = True
            cur.append(two); i += 2; continue
        if two == "]]":
            inlink = False
            cur.append(two); i += 2; continue
        if c == "|" and depth == 0 and not inlink:
            args.append("".join(cur).strip()); cur = []
        else:
            cur.append(c)
        i += 1
    if cur:
        args.append("".join(cur).strip())
    return args

ORDINAL_UNITS = {"": "", "1": "", "2": "", "3": ""}

def expand_convert(args):
    """{{convert|97.5|–|306.6|kg|lb}} -> '97.5 - 306.6 kg' (no unit maths, keep what is there)."""
    vals = [a.strip() for a in args if a and "=" not in a]
    if not vals:
        return ""
    num = re.compile(r"^[-+]?[\d,.]+$")
    parts = []
    for a in vals:
        if num.match(a) or a in ("–", "—", "-", "to", "and"):
            if a in ("–", "—", "to", "and") and not parts:
                break
            parts.append(a)
            continue
        if re.fullmatch(r"[A-Za-zµ°˚%/²³\.\-\s]+", a) and a not in parts:
            parts.append(a)
            break
        break
    return " " + " ".join(parts) + " " if parts else " "

def expand_named(name, args):
    low = name.strip().lower()
    if low in ("convert", "cvt"):
        return " " + expand_convert(args) + " "
    if low == "age":
        vals = [a for a in args if a and "=" not in a]
        return " " + " ".join(vals[:1]) + " "
    if low in ("formatnum", "val", "e", "num"):
        vals = [a for a in args if a and "=" not in a]
        return " " + ",".join(vals[:1]) if low == "formatnum" else (" " + " ".join(vals) + " ")
    if low in ("plainlist", "flatlist", "unbulleted list", "hlist"):
        vals = [re.sub(r"^\*?\s*", "", a) for a in args if a and not a.lstrip().startswith(("*", "|"))]
        return " " + ", ".join(vals) + " "
    if low in ("nowrap", "abbr", "sup"):
        vals = [a for a in args if a and "=" not in a]
        return " " + " ".join(vals) + " "
    if "date" in low or low in ("year", "decade", "month"):
        vals = [a for a in args if a and "=" not in a]
        return " " + " ".join(vals) + " "
    return None  # caller decides keep/drop

def find_templates(text):
    """Yield (start, end, name, body) for top-level {{...}} spans."""
    spans, stack, i = [], [], 0
    while True:
        a = text.find("{{", i)
        b = text.find("}}", i)
        if a == -1 and b == -1:
            break
        if a != -1 and (b == -1 or a < b):
            stack.append(a); i = a + 2
        else:
            if stack:
                s = stack.pop()
                if not stack:
                    body = text[s + 2:b]
                    name = re.split(r"[\|:]", body, 1)[0].strip()
                    spans.append((s, b + 2, name, body))
            i = b + 2
    for s in stack:  # unbalanced {{: treat through the next blank line as one template
        e = text.find("\n\n", s + 2)
        if e == -1:
            e = min(len(text), s + 30000)
        body = text[s + 2:e]
        name = re.split(r"[\|:]", body, 1)[0].strip()
        spans.append((s, e, name, body))
    return spans

def expand_templates(text, depth=0):
    if depth > 6:
        return text
    spans = find_templates(text)
    if not spans:
        return text
    out, last = [], 0
    # process innermost-first: sort longest-span first only at this level
    for s, e, name, body in spans:
        out.append(text[last:s])
        last = e
        args = split_template_args(body)
        if args:
            args = args[1:]  # drop the template-name segment
        base = re.sub(r"^.*:", "", name).strip()
        base = base.replace("_", " ").strip()
        inner = ""
        keep = expand_named(name, args)
        if keep is not None:
            inner = keep
        elif DROP_NAMES.match(base):
            inner = ""
        elif INFIX_KEEP.match(base):
            vals = [a for a in args if a and "=" not in a]
            inner = " " + " ".join(vals) + " "
        elif base.lower().startswith(("infobox", "taxobox", "chemicalbox", "speciesbox")):
            inner = expand_infobox(args)
        elif base.lower().startswith(("file:", "image")):
            inner = ""
        else:
            # unknown template: keep only positional args that look like content
            vals = [a for a in args if a and "=" not in a and len(a) < 400]
            inner = expand_templates(" " + " ".join(vals) + " ", depth + 1) if vals else ""
        if inner:
            out.append(inner)
    out.append(text[last:])
    return "".join(out)

INFO_SKIP = re.compile(r"^(image|map|logo|symbol|caption|leader ?title\d*|currency|blank.*|setup.*|unit.*|size.*|website|footnotes?|module)$", re.I)
INFO_KEEP = re.compile(r"(population|gdp|area|capital|largest|demonym|establish|found|leader|president|prime ?minister|monarch|pm|chancellor|density|total|urban|rank|hd ?i|hdi|life|expectancy|crime|homicide|percent|per ?capita|nominal|ppp|currency|ethnic|religion|language|literacy|income|unemployment|birth|death|water|forest|electricity|internet|subdivision|anthem|currency_code)", re.I)

def expand_infobox(args):
    lines = []
    for a in args:
        if "=" not in a:
            continue
        key, _, val = a.partition("=")
        key = key.strip().lower().replace("_", " ")
        val = val.strip()
        if not val or INFO_SKIP.search(key) or not INFO_KEEP.search(key):
            continue
        val = re.sub(r"\s+", " ", val)
        if len(val) > 300:
            continue
        lines.append(f"{key}: {val}")
    return ("\n" + "\n".join(lines) + "\n") if lines else ""

# ---------------------------------------------------------------- wikitext -> text

REF_RE = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>|<!--.*?-->", re.S)
TAG_RE = re.compile(r"</?(?:span|div|small|big|br|p|center|poem|blockquote|e?[im]s|u|s|sub|sup|nowrap|includeonly|onlyinclude|noinclude|templatestyles|score|chem|math)(?:\s[^>]*)?>", re.I)
MATH_RE = re.compile(r"<math[^>]*>.*?</math>", re.S)
STYLE_RE = re.compile(r"<(?:style|script)[^>]*>.*?</(?:style|script)>", re.I | re.S)
LINK_RE = re.compile(r"\[\[(?:[A-Za-z0-9 ._:§/–'\"-]*\|)?([^\[\]\n|]+)\]\]")
CAT_LINK = re.compile(r"\[\[(?:Category|Image|File|Media|Talk|Wikipedia|Help|Portal|Template|Special|Draft|User|Wikipedia talk|Template talk)(?::[^\]|]*)(?:\|[^\]\n]*)?\]\]", re.I)
EXT_RE = re.compile(r"\[http[^\s\]\|]+\s+([^\]\|\n]+)\]|\[http[^\]\n\|]*\]")
HEAD_RE = re.compile(r"^\s*(={2,6})\s*(.*?)\s*\1\s*$", re.M)
TABLE_MARK = re.compile(r"^\s*(\{\||\|\}|\{\{|!!)")
ROW_MARK = re.compile(r"^\s*(\|-?|\!|^\|)\s*")

def wikitext_to_text(raw):
    t = raw
    t = STYLE_RE.sub(" ", t)
    t = REF_RE.sub(" ", t)
    t = MATH_RE.sub(" ", t)
    t = expand_templates(t)
    for _ in range(3):
        t2 = re.sub(r"\{\{[^{}]*\}\}", " ", t)
        t2 = re.sub(r"\{\{[^{}]*$|^[^{}]*\}\}", " ", t2, flags=re.M)
        if t2 == t:
            break
        t = t2
    t = t.replace("{{", " ").replace("}}", " ")
    # tables: drop structural lines/pipes, keep cell text
    lines = []
    for ln in t.split("\n"):
        if TABLE_MARK.match(ln) or ROW_MARK.match(ln):
            ln = re.sub(r"^\s*(\{\||\|\}|\{\{|!!|\|-?|\!|^\|)\s*", "", ln)
            ln = re.sub(r"^\s*(class|style|align|bgcolor|width|rowspan|colspan)\s*=\s*[^|]*\|?", "", ln)
            if re.fullmatch(r"[\s\-–—|:=]*", ln or ""):
                continue
        lines.append(ln)
    t = "\n".join(lines)
    t = TAG_RE.sub(" ", t)
    t = CAT_LINK.sub(" ", t)
    t = LINK_RE.sub(r"\1", t)
    t = EXT_RE.sub(r"\1", t)
    t = HEAD_RE.sub(r"\n\2\n", t)
    t = re.sub(r"'''*|''", "", t)
    t = re.sub(r"^\s*[:*#;]+\s*", lambda m: " " if len(m.group(0)) <= 2 else "", t, flags=re.M)
    t = re.sub(r"[ \t]{2,}", " ", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    t = html.unescape(t)
    return t.strip()

# ---------------------------------------------------------------- driver

DOC_RE = re.compile(r"<doc id=", re.M)

def process_batch(pages):
    out = []
    for doc_id, title, text in pages:
        body = wikitext_to_text(text)
        body = "\n".join(ln.strip() for ln in body.split("\n")).strip()
        if len(body) < 80:
            continue
        t = title.replace('"', "'").replace("\n", " ")
        url = f"https://en.wikipedia.org/wiki?curid={doc_id}"
        out.append(f'<doc id="{doc_id}" url="{url}" title="{t}">\n{body}\n</doc>\n')
    return out

def _part_sort_key(p):
    m = re.search(r"multistream\d+\.xml-p(\d+)p(\d+)", p)
    return (int(m.group(1)) if m else 10 ** 12, p)

def iter_pages():
    parts = sorted(glob.glob(os.path.join(ARCHIVE, "*pages-articles*.bz2")), key=_part_sort_key)
    page_re = re.compile(r"<page>(.*?)</page>", re.S)
    title_re = re.compile(r"<title>(.*?)</title>", re.S)
    id_re = re.compile(r"<id>(\d+)</id>")
    text_re = re.compile(r"<text[^>]*>(.*?)</text>", re.S)
    for part in parts:
        dec = bz2.BZ2File(part)
        carry = b""
        while True:
            chunk = dec.read(1 << 22)
            if not chunk:
                break
            data = carry + chunk
            idx = data.rfind(b"</page>")
            if idx == -1:
                carry = data
                continue
            blob = data[:idx + 7].decode("utf-8", "replace")
            carry = data[idx + 7:]
            for m in page_re.finditer(blob):
                pg = m.group(1)
                tm = title_re.search(pg)
                im = id_re.search(pg)
                xm = text_re.search(pg)
                if not (tm and im and xm):
                    continue
                yield im.group(1), html.unescape(tm.group(1)), xm.group(1)
        if carry:
            blob = carry.decode("utf-8", "replace")
            for m in page_re.finditer(blob):
                pg = m.group(1)
                tm = title_re.search(pg)
                im = id_re.search(pg)
                xm = text_re.search(pg)
                if tm and im and xm:
                    yield im.group(1), html.unescape(tm.group(1)), xm.group(1)
        dec.close()

def main():
    out_dir = sys.argv[1]
    workers = 12
    limit = 0
    args = sys.argv[2:]
    for i, a in enumerate(args):
        if a == "--workers": workers = int(args[i + 1])
        if a == "--limit": limit = int(args[i + 1])
    os.makedirs(out_dir, exist_ok=True)
    shard, buf, docs, t0 = 0, [], 0, __import__("time").time()
    pool = Pool(workers)
    pending, it = [], iter_pages()
    done = 0
    with pool:
        batch, pages = [], []
        def flush_batch():
            nonlocal batch, pages
            if pages:
                pending.append(pool.apply_async(process_batch, (pages,)))
                while len(pending) > workers * 2:
                    r = pending.pop(0).get()
                    write(r)
            batch, pages = [], []
        def write(docs_out):
            nonlocal shard, buf, docs
            for d in docs_out:
                buf.append(d); docs += 1
                if len(buf) >= OUT_DOCS_PER_SHARD:
                    _flush_shard(out_dir, shard, buf); shard += 1; buf = []
        for pid, title, text in it:
            ns = title.find(":")
            if ns != -1 and title[:ns].strip().lower() in {
                "talk", "user", "wikipedia", "wikipedia talk", "file", "image",
                "media", "template", "template talk", "help", "category", "portal",
                "special", "draft", "draft talk", "module", "timedtext", "book",
            }:
                continue
            if text.lstrip().lower().startswith("#redirect"):
                continue
            pages.append((pid, title, text))
            if len(pages) >= 200:
                flush_batch()
            done += 1
            if limit and done >= limit:
                break
            if done % 50_000 < 200:
                print(f"[{done} pages, shard {shard}, {time.time()-t0:.0f}s]", flush=True)
        flush_batch()
        for task in pending:
            write(task.get())
    if buf:
        _flush_shard(out_dir, shard, buf)
    print(f"[done] {docs} docs -> {out_dir} in {time.time()-t0:.0f}s", flush=True)

def _flush_shard(out_dir, shard, buf):
    with open(os.path.join(out_dir, f"shard_{shard:05d}"), "w", encoding="utf-8") as fh:
        fh.write("".join(buf))

import time
if __name__ == "__main__":
    main()
