# Attachment reasoning fixtures

Synthetic text files and questions that reproduce failures reported from an offline
emulator test of Field Atlas 1.2.0: a citation attached to the wrong revision, a
maintenance-hold exception ignored, a missing item answered with unrelated details, and a
wrong 12-hour time conversion. None of the content is real.

`questions.json` maps each question to the files attached to it. Run them through the app's
own attachment pipeline on desktop:

```sh
python3 - <<'EOF'
import json, subprocess
questions = json.load(open("fixtures/attachment-reasoning/questions.json"))
args = ["python3", "scripts/run_desktop_research.py", "--model-only",
        "--attachments", "fixtures/attachment-reasoning/questions.json",
        "--output", "build/desktop-evaluation/attachment-reasoning/report.json"]
for question in questions:
    args += ["--question", question]
subprocess.run(args, check=True)
EOF
```

A completed run is not a score; read each answer against the files.
