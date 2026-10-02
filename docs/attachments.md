# Research with your files

This feature is in v1.2.0 (and was first published in 1.2.0-rc.1); the older v1.1.10 APK does not have it. Offline reader tests and TXT/PDF/photo picker checks have passed on an Infinix running Android 16. Real camera capture, broader model quality, and other devices still need verification.

Tap the paperclip in Research, then choose **Camera**, **Photos**, or **Files**. Wait for **Ready**, enter your question, and start research. Long document names keep their beginning and ending visible. Tap a ready document to check its extracted text. Images appear as small thumbnails: tap to preview, pinch or double-tap to zoom, or use the zoom buttons. Choose **Recognized text** to check the extracted words. Close the preview with X; remove an attachment with its row's X or **Remove image**. Removing it does not delete your original.

With files attached, answers use **your files by default**, without searching the library. If they do not contain the answer, the app is instructed to say what is missing. To include installed knowledge as well, explicitly add **“also use my library”** to your question. Without attachments, research uses your enabled collections as before.

Library opt-in currently recognizes clear English requests such as “use my library,” “include the library,” or “compare these notes with my library.” Ambiguous, quoted, or exclusionary wording stays files-only. Other languages can still be used for file questions, but their library opt-in wording is not yet recognized. Citations identify each file or library source; a statement in one source should not be treated as true of another. This boundary limits supplied evidence, but does not guarantee that every model will answer correctly.

| Input | What Field Atlas reads |
| --- | --- |
| Camera or photo | Printed English text, recognized on the phone. Not objects or general image understanding. |
| TXT, Markdown, CSV, JSON | Plain text saved as UTF-8 or BOM-marked UTF-16. |
| PDF | Embedded text on Android 15+, plus a local OCR check of every page so text inside images is not skipped. Android 13–14 uses printed-English OCR only. OCR adds processing time and may introduce errors. |

JPEG, PNG and WebP images are accepted. There is no support for Office documents, password-protected PDFs, handwriting recognition guarantees, or all OCR languages.

## Limits and honest answers

Attach up to **3 files**, each at most **20 MiB**. PDFs may have up to **30 pages** and each document may contain up to **100,000 extracted characters**. Larger files are rejected with an explanation; they are not silently imported in part.

Attachments appear in a sideways-scrolling strip inside the question box. Use the X at the top-right of a tile to remove it. The paperclip is disabled when three attachments are selected; remove one to add another. Files that are still being read or could not be read also count toward this limit.

Answers use selected excerpts that fit the loaded model, not necessarily the whole document. Smaller contexts fit fewer details. Citations open the exact supplied passage and its filename/page. OCR can make mistakes: check the recognized text before relying on it. A menu mentioning vegan food is evidence about that menu, not proof of current availability or a best-restaurant ranking.

If a PDF page has no readable text, the attachment preview lists the affected pages. Such pages may be blank, contain graphics, or have failed OCR; their contents are not silently treated as reviewed. Hybrid text/image pages are also OCR-checked.

This text-based path works independently of vision support, with models the existing Field Atlas runtime can load. It does not make incompatible model formats work. Actual multi-model attachment quality and GrapheneOS behavior require device testing.

## Privacy and storage

Nothing is uploaded. OCR data is bundled with the app, so reading does not require a first-use download or Play Services. Choose files already saved on the phone: an Android cloud file provider may need its own connection to supply a file.

Temporary copies stay in app-private cache. Draft attachments survive screen rotation but are not restored after process death. Removing a draft attachment or starting a new question removes its staged copy; abandoned staged copies are cleared when the app starts. Originals are never deleted. Completed attachment-backed answers retain their cited text excerpts in History; removing the draft attachment does not erase those saved excerpts.

## Reproducible OCR assets

- Wrapper: `cz.adaptech.tesseract4android:tesseract4android:4.9.0`, via a group-restricted JitPack repository.
- AAR SHA-256: `bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a`.
- English data: [tessdata_fast pinned revision](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), `eng.traineddata`.
- Data SHA-256: `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`. Included at `app/src/main/assets/ocr/eng.traineddata` and checked before OCR initialization.
- The wrapper includes Tesseract 5.5.1, Leptonica 1.85.0, libjpeg 9f and libpng 1.6.48. [Upstream build and usage](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0).
- Redistributed license texts are bundled under `app/src/main/assets/ocr/licenses/`. This software is based in part on the work of the Independent JPEG Group.
- PDFs use Android's platform renderer, not an additional downloaded parser. System security updates matter for processing untrusted PDFs.

Build once with network access to resolve the pinned Gradle dependency, then use `--offline` for cached builds. Runtime OCR assets need no download.
