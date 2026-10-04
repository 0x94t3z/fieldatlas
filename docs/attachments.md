# Research with your files

This feature has been in Field Atlas since v1.2.0 (first published in 1.2.0-rc.1). Document formats, text-layer PDFs, partial reading and restore after the app is closed are new in the next release; this page describes the current source. Offline reader tests and TXT/PDF/photo picker checks have passed on an Infinix running Android 16. Real camera capture, broader model quality, and other devices still need verification.

Tap the paperclip in Research, then choose **Camera**, **Photos**, or **Files**. Wait for **Ready**, enter your question, and start research. Long document names keep their beginning and ending visible. Tap a ready document to check its extracted text. Images appear as small thumbnails: tap to preview, pinch or double-tap to zoom, or use the zoom buttons. Choose **Recognized text** to check the extracted words. Close the preview with X; remove an attachment with its row's X or **Remove image**. Removing it does not delete your original.

With files attached, answers use **your files by default**, without searching the library. If they do not contain the answer, the app is instructed to say what is missing. To include installed knowledge as well, explicitly add **“also use my library”** to your question. Without attachments, research uses your enabled collections as before.

Library opt-in currently recognizes clear English requests such as “use my library,” “include the library,” or “compare these notes with my library.” Ambiguous, quoted, or exclusionary wording stays files-only. Other languages can still be used for file questions, but their library opt-in wording is not yet recognized. Citations identify each file or library source; a statement in one source should not be treated as true of another. This boundary limits supplied evidence, but does not guarantee that every model will answer correctly.

| Input | What Field Atlas reads |
| --- | --- |
| Camera or photo | Printed English text, recognized on the phone. Not objects or general image understanding. JPEG, PNG, WebP, HEIC/HEIF, GIF (first frame) and BMP. |
| Text and code | TXT, Markdown, CSV, JSON, HTML (shown as readable text), source code and config files. UTF-8, UTF-16 (with or without a byte-order mark) and Windows-1252. |
| PDF | The embedded text layer on every supported Android version. Only pages whose text layer is empty or nearly empty (scans, photographed pages, a header over an image) go through local OCR. |
| Word, PowerPoint, OpenDocument, EPUB | `.docx`, `.pptx`, `.odt`, `.odp` and `.epub`, read on the phone. Slides and EPUB chapters keep their numbers for citations. |

Older binary Office files (`.doc`, `.xls`, `.ppt`), spreadsheets and archives are refused with a message saying how to convert them. Password-protected PDFs are refused. Handwriting and non-English OCR are not supported.

## Limits and honest answers

Attach up to **3 files**, each at most **50 MiB**. A file is read up to **300,000 characters**, a PDF up to **1,000 pages**, and at most **30 scanned PDF pages** go through OCR (it is slow on a phone). Anything past a limit is not refused: the file is read up to the limit, and the preview and the answer say what was not read.

Attachments appear in a sideways-scrolling strip inside the question box. Use the X at the top-right of a tile to remove it. The paperclip is disabled when three attachments are selected; remove one to add another. Files that are still being read or could not be read also count toward this limit.

Answers use the passages that best match the question, not necessarily the whole document. Files are split at paragraph and sentence boundaries, and passages are ranked by how well they match the question's distinctive words (common words and question words are ignored; part codes such as K-9 and K9 match). A summary question takes passages spread evenly through the file instead. About 1,800 tokens of file text go to the model per answer, which keeps the wait on a slow phone reasonable.

When an answer used only part of a file, the app adds a line naming how many passages it used and from which pages, slides or chapters. The app writes this line itself, so it does not depend on the model remembering to say so. Citations open the exact supplied passage and its filename and page. OCR can make mistakes: check the recognized text before relying on it. A menu mentioning vegan food is evidence about that menu, not proof of current availability or a best-restaurant ranking.

If a PDF page has no readable text, the attachment preview lists the affected pages. Such pages may be blank, contain graphics, or have failed OCR; their contents are not silently treated as reviewed.

This text-based path works independently of vision support, with models the existing Field Atlas runtime can load. It does not make incompatible model formats work.

## Privacy and storage

Nothing is uploaded. OCR data is bundled with the app, so reading does not require a first-use download or Play Services. Choose files already saved on the phone: an Android cloud file provider may need its own connection to supply a file.

Temporary copies stay in app-private cache. Draft attachments survive screen rotation and are restored, and read again, if Android closes the app while it is in the background (for example while a file picker or camera is open). Removing a draft attachment or starting a new question removes its staged copy; abandoned staged copies are cleared when the app starts. Originals are never deleted. Completed attachment-backed answers retain their cited text excerpts in History; removing the draft attachment does not erase those saved excerpts.

## Reproducible OCR assets

- Wrapper: `cz.adaptech.tesseract4android:tesseract4android:4.9.0`, via a group-restricted JitPack repository.
- AAR SHA-256: `bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a`.
- English data: [tessdata_fast pinned revision](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), `eng.traineddata`.
- Data SHA-256: `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`. Included at `app/src/main/assets/ocr/eng.traineddata` and checked before OCR initialization.
- The wrapper includes Tesseract 5.5.1, Leptonica 1.85.0, libjpeg 9f and libpng 1.6.48. [Upstream build and usage](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0).
- Redistributed license texts are bundled under `app/src/main/assets/ocr/licenses/`. This software is based in part on the work of the Independent JPEG Group.
- PDF text layers are read with PdfBox-Android `2.0.27.0` (Apache-2.0), bundled in the APK; scanned pages are drawn with Android's platform renderer for OCR. Neither downloads anything. System security updates still matter for processing untrusted PDFs.
- Word, PowerPoint, OpenDocument and EPUB files are read with the Java ZIP reader and a small text extractor in the app. Archives are capped (entries, per-entry and total inflated size) so a crafted file cannot exhaust memory.

Build once with network access to resolve the pinned Gradle dependency, then use `--offline` for cached builds. Runtime OCR assets need no download.
