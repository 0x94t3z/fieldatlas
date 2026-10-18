#!/usr/bin/env python3
"""Measure Field Atlas's memory while it answers, on an Android emulator or a rooted test device.

For each question the app is asked through its own screen (as a person would), and three figures are recorded:

- peak_rss_kb: the kernel's high-water mark of the app's resident memory (VmHWM in /proc/PID/status), reset before
  each answer by writing 5 to /proc/PID/clear_refs. Nothing between samples can be missed. RSS counts every shared
  page in full, so it is the worst case.
- peak_total_pss_kb: Android's own "memory this app is responsible for" (TOTAL PSS in `dumpsys meminfo`), read
  every second, with TOTAL SWAP PSS beside it.
- least_available_kb: the least memory the whole device had available (MemAvailable in /proc/meminfo).

All values are in kB (1,024 bytes) as the kernel and dumpsys give them; multiply by 1,024 for bytes. The model file is
memory-mapped, so its pages count in RSS and PSS although Android can drop and re-read them: both figures overstate
what the app needs. After a run, `adb shell dumpsys activity exit-info xyz.fieldatlas` shows whether Android ever
stopped the app for low memory.

Needs `adb root` (an emulator image without Google Play, or a rooted test device), the app installed with a model,
and the screen unlocked on the Research page. Usage:

    adb root
    python3 scripts/measure_answer_memory.py --model "Qwen3.5 4B" --out memory.jsonl \\
        --ask "why did the Roman Republic turn into an empire" --follow-up "what part did Julius Caesar play in that"
"""
import argparse
import json
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

PKG = "xyz.fieldatlas"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    out = []
    for n in ET.fromstring(adb("exec-out", "cat", "/sdcard/ui.xml")).iter("node"):
        box = list(map(int, re.findall(r"\d+", n.get("bounds", ""))))
        if len(box) == 4:
            out.append((n.get("text", ""), n.get("content-desc", ""), box))
    return out


def has(label):
    return any(label in (text, desc) for text, desc, _ in nodes())


def tap(label):
    # Never tap blind: exactly one node may carry the label.
    hits = [box for text, desc, box in nodes() if label in (text, desc)]
    if len(hits) != 1:
        raise SystemExit(f"{len(hits)} nodes match {label!r}")
    x1, y1, x2, y2 = hits[0]
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def type_text(text):
    # Long `input text` calls drop characters on some devices; short chunks do not.
    for i in range(0, len(text), 12):
        adb("shell", "input", "text", text[i:i + 12].replace(" ", "%s").replace("'", "\\'").replace("?", "\\?"))
        time.sleep(0.15)


def pid():
    for _ in range(30):
        found = adb("shell", f"pidof {PKG}", check=False).strip()
        if found:
            return found
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
        time.sleep(2)
    raise SystemExit("the app is not running")


def peak_rss_kb(process):
    return int(re.search(r"VmHWM:\s+(\d+)", adb("shell", f"cat /proc/{process}/status")).group(1))


def available_kb():
    return int(re.search(r"MemAvailable:\s+(\d+)", adb("shell", "cat /proc/meminfo")).group(1))


def pss_kb():
    found = re.search(r"TOTAL PSS:\s+(\d+)\s+TOTAL RSS:\s+(\d+)\s+TOTAL SWAP PSS:\s+(\d+)", adb("shell", f"dumpsys meminfo {PKG}"))
    return tuple(map(int, found.groups())) if found else None


def ask(question, follow_up):
    if follow_up:
        tap("Follow-up question"); time.sleep(1); type_text(question); time.sleep(1); tap("Ask follow-up")
        return
    # Back leaves the app from a tab's top level, so open it again each time and go to the Research tab.
    for _ in range(6):
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(2)
        if has("Research question"):
            break
        tab = [box for _, desc, box in nodes() if desc == "Research"]
        if len(tab) == 1:
            x1, y1, x2, y2 = tab[0]
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)); time.sleep(2)
            if has("Research question"):
                break
        adb("shell", "input", "keyevent", "4"); time.sleep(2)
    tap("Research question"); time.sleep(1); type_text(question); time.sleep(1)
    adb("shell", "input", "keyevent", "4"); time.sleep(1); tap("Start research")


def measure(model, question, follow_up, timeout):
    process = pid()
    adb("shell", f"echo 5 > /proc/{process}/clear_refs")  # resets VmHWM to the current resident set
    samples, stop = [], threading.Event()

    def sample():
        while not stop.is_set():
            pss, available = pss_kb(), available_kb()
            if pss:
                samples.append((pss, available))
            time.sleep(1)

    sampler = threading.Thread(target=sample)
    sampler.start()
    start = time.time()
    ask(question, follow_up)
    time.sleep(8)
    # The top bar's share action appears only once the answer is finished.
    while time.time() - start < timeout and not has("Share answer with sources"):
        time.sleep(4)
    seconds = round(time.time() - start)
    time.sleep(3)
    stop.set(); sampler.join()
    same = pid() == process
    return {"model": model, "question": question, "follow_up": follow_up, "seconds": seconds, "finished": seconds < timeout,
            "same_process": same, "peak_rss_kb": peak_rss_kb(process) if same else None,
            "peak_total_pss_kb": max(s[0][0] for s in samples), "peak_swap_pss_kb": max(s[0][2] for s in samples),
            "least_available_kb": min(s[1] for s in samples), "samples": len(samples)}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--model", required=True, help="the model in use, as a label for the results")
    parser.add_argument("--out", required=True, help="JSON Lines file the results are appended to")
    parser.add_argument("--ask", action="append", default=[], help="a question asked as a new topic")
    parser.add_argument("--follow-up", action="append", default=[], help="a follow-up asked under the previous answer")
    parser.add_argument("--timeout", type=int, default=900, help="seconds to wait for one answer")
    args = parser.parse_args()
    # Keep the order given on the command line: a follow-up goes under the answer before it.
    plan = [(sys.argv[i + 1], flag == "--follow-up") for i, flag in enumerate(sys.argv[:-1]) if flag in ("--ask", "--follow-up")]
    with open(args.out, "a") as out:
        for question, follow_up in plan:
            row = measure(args.model, question, follow_up, args.timeout)
            out.write(json.dumps(row) + "\n"); out.flush()
            print(json.dumps(row), flush=True)


if __name__ == "__main__":
    main()
