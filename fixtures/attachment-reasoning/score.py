#!/usr/bin/env python3
"""Score desktop answers to the attachment-reasoning fixtures against rules taken from the files.

Each rule names one failure the original emulator test reported or a fact a correct answer
must keep. The rules are keyword checks: they catch the reported mistakes, not every possible
wrong answer, so a pass is a screen, not a guarantee. Usage: score.py report.json [more.json]
"""
import json
import re
import sys
from collections import defaultdict

HELIOS_2345 = "My Helios HX-44 serial is 2345. Compare the saved revisions: which filter, connector and service interval apply, and why?"
HELIOS_1500 = "My Helios HX-44 serial is 1500. Which filter and service interval apply?"
GARDEN = "Use the garden irrigation policy. Should East and West be watered today? Give a decision and reason for each."
WIFI = "What is the Wi-Fi password at Cedar Lodge? Use my saved travel notes."
PHONE = "What is the phone number for Cedar Lodge?"
SHUTTLE = "When does the last shuttle to Cedar Lodge leave? Give the time in 12-hour format too."
LATE = "If my train is 90 minutes late, can I still check in at Cedar Lodge tonight? Where should I sleep?"


def sentences(text):
    return [s for s in re.split(r"(?<=[.!?])\s+|\n+", text) if s.strip()]


def cited(sentence):
    return set(re.findall(r"\[S?(\d+)]", sentence))


def rules(question):
    """(name, predicate) pairs; a predicate returns True when the answer is right on that point."""
    i = re.IGNORECASE
    if question == HELIOS_2345:
        return [
            ("names K-9, M5 and 600 h", lambda a: all(re.search(p, a, i) for p in (r"K-?9", r"\bM5\b", r"\b600\b"))),
            ("no false serial range claim", lambda a: not re.search(r"(greater than|exceeds?|above|over) (serial )?2999|falls under (\*\*)?revision a", a, i)),
            ("does not apply A's values to 2345", lambda a: not any(re.search(r"K-?7|\bM2\b|\b400\b", s, i) and re.search(r"(this|your|must|use|apply|applies|requires)", s, i)
                                                                    and not re.search(r"revision a|1000|1999|not|older|previous|instead", s, i) for s in sentences(a))),
            ("Revision B claims never cite only [S1]", lambda a: not any(cited(s) == {"1"} and re.search(r"revision b|K-?9|\bM5\b|\b600\b", s, i)
                                                                         and not re.search(r"revision a|K-?7|\bM2\b|\b400\b", s, i) for s in sentences(a))),
        ]
    if question == HELIOS_1500:
        return [
            ("names K-7 and 400 h", lambda a: re.search(r"K-?7", a, i) and re.search(r"\b400\b", a)),
            ("does not put 1500 under Revision B", lambda a: not re.search(r"falls under (\*\*)?revision b", a, i)),
        ]
    if question == GARDEN:
        return [
            ("East not watered", lambda a: re.search(r"(do not|don't|not|cannot|can't|should not|shouldn't|must not|no)\b[^.]{0,40}\bwater[^.]{0,30}\beast|\beast\b[^.]{0,80}\b(not|cannot|can't|no)\b[^.]{0,20}\b(be )?water|\beast\b[^.]{0,40}\b(is |be )?(excluded|deferred|delayed|on hold|must wait)|(delay|defer|skip|exclude)[^.]{0,20}\beast|\beast\b[^.]{0,40}\b(must|should|has to|needs to) wait|(be )?watered only after|only (the )?west\b[^.]{0,30}(should|must|can|be|is)? ?(be )?water|water (the )?west (plot )?only", a, i)),
            ("West watered", lambda a: re.search(r"\bwest\b[^.]{0,120}\b(should|must|can|qualif|eligible|water today|be watered|requires? (irrigation|water))|water[^.]{0,40}\bwest\b", a, i)),
            ("no 'despite the hold' or both-watered decision", lambda a: not re.search(r"despite the hold|decision: water (both|east)|water both|both (plots )?(should|must|need to) be (water|irrigat)|east and west (plots )?(should|must|need to) be (water|irrigat)[^.,]*\.|east[^.]{0,60}decision: water today", a, i)),
            ("no false claim that both plots have holds", lambda a: not re.search(r"both plots have (an )?active (maintenance )?holds?", a, i)),
        ]
    if question in (WIFI, PHONE):
        return [("says the file doesn't mention it", lambda a: re.search(r"doesn't mention|does not mention|not (included|listed|provided|mentioned)|no (wi-?fi )?(password|phone number)", a, i)),
                ("no unrelated details", lambda a: len(a) < 260)]
    if question == SHUTTLE:
        return [
            ("gives 9:30 PM", lambda a: re.search(r"9:30\s*p\.?m", a, i)),
            ("no wrong conversion", lambda a: not re.search(r"(10|8|11):30\s*p\.?m\.?\s*\(?\s*21:30|21:30\s*\(\s*(10|8|11):30", a, i)),
            ("no wrong arithmetic about 21:10", lambda a: not re.search(r"(one|1) hour (after|later)|an hour after", a, i)),
        ]
    if question == LATE:
        return [
            ("says no check-in tonight", lambda a: re.search(r"(cannot|can't|can not|not be able to|won't be able to|unable to) (still )?check in|too late", a, i)),
            ("Station Hotel transport stated correctly", lambda a: not re.search(r"(can|will|could) (assist|help) with transport|(arranges?|provides?) (its own )?transport(?! arrangements)", a, i)
                                                       or re.search(r"(does not|doesn't|no) (arrange|provide)", a, i)),
            ("arrival time right if stated", lambda a: not re.search(r"\b22:(00|10|30)\b[^.]{0,20}(arriv)|arriv[^.]{0,40}\b(around |about |at )?22:(00|10)\b", a, i)),
        ]
    return []


def main(paths):
    totals = defaultdict(lambda: [0, 0])
    failures = defaultdict(lambda: defaultdict(int))
    for path in paths:
        for run in json.load(open(path))["runs"]:
            answer = run["visibleAnswer"]
            checks = rules(run["question"])
            ok = all(bool(predicate(answer)) for _, predicate in checks)
            totals[run["question"]][0] += ok
            totals[run["question"]][1] += 1
            for name, predicate in checks:
                if not predicate(answer):
                    failures[run["question"]][name] += 1
    passed = sum(p for p, _ in totals.values())
    count = sum(n for _, n in totals.values())
    for question, (p, n) in totals.items():
        print(f"{p}/{n}  {question[:70]}")
        for name, k in failures[question].items():
            print(f"        {k}x {name}")
    print(f"TOTAL {passed}/{count}")


if __name__ == "__main__":
    main(sys.argv[1:])
