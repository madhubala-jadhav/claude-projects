# ADR-0016 — PDF parsing in Java: Apache PDFBox for text/tables, tess4j for OCR

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Supersedes:** [ADR-0003](ADR-0003-pdf-parsing-strategy.md) (library choice only — the per-page cascade design it decided is kept, see below)

---

## Context

[ADR-0014](ADR-0014-language-and-runtime-java-override.md) commits the implementation to
Java 17, leaving [ADR-0003](ADR-0003-pdf-parsing-strategy.md)'s library choices (`pdfplumber`
for text/table extraction, `pdf2image` + `pytesseract` for OCR) without a runtime. This ADR
re-picks the libraries only. **Every part of ADR-0003 that is not a library name still
holds**: the per-page cascade (classify each page by usable-text-layer, text path first, OCR
only for image-only pages), the line-reassembly rule for wrapped descriptions (EC2), the
header/footer-suppression config, `bank_profiles.yaml`'s `pdf_table` block as the per-bank
template mechanism, word-level OCR confidence driving `needs_review`, and Tesseract remaining
an optional native dependency whose absence degrades FR3 (`PARSE-202`) rather than failing
the run. Those design decisions were never Python-specific.

ADR-0001's own survey (written before the language was decided) already evaluated the two
realistic Java candidates and rejected both only because Python was available at the time —
neither was rejected on technical merit:

> Apache PDFBox gives text and positions; the table reconstruction is code you write
> yourself. `tabula-java` is closer but is heuristic and less controllable per-bank.

ADR-0003 §C also rejected `tabula-py` specifically *because* it drags a JRE into a Python
tool — an objection that inverts now that the tool **is** a JVM program.

## Decision

**Apache PDFBox for text extraction, table reconstruction, decryption and page rendering.
tess4j (a JNI/JNA wrapper over the same Tesseract binary the Python path would have shelled
out to) for OCR, unchanged as an optional dependency.**

The per-page cascade from ADR-0003 carries over with these library substitutions:

1. **Decryption.** `Loader.loadPDF(file, password)` (PDFBox 3.x). In-memory only, never
   logged, never persisted (§13) — same guarantee as before. Wrong/refused password ⇒
   `PARSE-201`, file excluded, run continues (FR5).
2. **Per-page classification.** Unchanged: a page has a usable text layer if it yields
   ≥ `min_chars_per_page` (default 40) characters via `PDFTextStripper`.
3. **Text path (FR2).** For each text page, extract words with bounding boxes by subclassing
   `PDFTextStripper` and overriding `writeString(String, List<TextPosition>)` — this is
   PDFBox's equivalent of `pdfplumber`'s raw-words-with-bounding-boxes model. Cluster words
   into rows/columns using the resolved profile's `pdf_table` settings (tolerance, crop
   bounds, header markers), exactly as ADR-0003 specified. Emit `RawRow`s with
   `confidence = 1.0` and `extractor = "pdf_text"`.
4. **Line reassembly (EC2), header/footer suppression.** Unchanged — these operate on the
   `RawRow` model, not on the extraction library.
5. **OCR path (FR3).** Rasterize image-only pages with PDFBox's built-in
   `org.apache.pdfbox.rendering.PDFRenderer` at `ocr.dpi` (default 300) to a `BufferedImage`
   — **this needs no external rendering binary**, unlike the Python path's `pdf2image`, which
   shells out to Poppler's `pdftoppm`. Feed each page image to tess4j's
   `Tesseract.getWords(BufferedImage, TessPageIteratorLevel.RIL_WORD)`, which returns
   word-level results with confidence, mirroring `pytesseract.image_to_data`. Cluster words
   into rows/columns by position exactly as ADR-0003 specified; row `confidence` = mean word
   confidence; below `ocr.confidence_threshold` (default 0.75) ⇒ `needs_review = true`.
6. **Nothing found.** Unchanged: `PARSE-203`.

**Templates stay configuration.** `bank_profiles.yaml`'s `pdf_table` block is unaffected by
this ADR — it describes clustering tolerances and crop regions, not a library API.

**Tesseract remains optional.** If tess4j cannot load the native Tesseract library (absent,
or `UnsatisfiedLinkError`), the OCR path is skipped and scanned files are excluded with
`PARSE-202` and an install hint, identical to the Python behavior.

## Alternatives considered

### A. `tabula-java` as the primary table engine — rejected

Now technically reachable without the cross-language objection that killed `tabula-py` in
ADR-0003 §C, but rejected on the same grounds ADR-0004 §C rejected fully-automatic column
detection: `tabula-java`'s heuristics are less controllable per bank than raw words with
bounding boxes, and a confidently-wrong table extraction is worse than a slower, tunable one.
Kept in reserve as a per-profile alternative engine, the same role ADR-0003 §B gave
`camelot` — worth trying if a specific bank's ruled table defeats PDFBox's clustering.

### B. iText — rejected

iText 7+ is AGPL/commercial-licensed, the same class of objection that ruled out `PyMuPDF` in
ADR-0003 §D. PDFBox is Apache 2.0, which also keeps it consistent with the licensing of
[ADR-0015](ADR-0015-tabular-parsing-java-apache-poi.md)'s Apache POI and Commons CSV choices.

### C. OCR everything, uniformly — rejected, same reasoning as ADR-0003 §A

Unchanged by language: throws away exactness on the ~90% text-based case, threatens NFR2,
and makes Tesseract a hard requirement.

### D. An LLM/vision model to read statements — rejected outright, same reasoning as ADR-0003 §E

Unchanged by language: a hosted model violates NFR1/AC8/§13; a local model adds
non-determinism to a task an exact extractor already solves.

### E. `pdf2image`-equivalent via a bundled Poppler binary — rejected

Not needed. PDFBox's own `PDFRenderer` rasterizes pages without any external binary, which is
strictly better than the Python path's Poppler dependency, not just a substitute for it.

## Consequences

**Positive**

- Page rasterization for OCR needs **no external rendering binary** — PDFBox replaces both
  `pdfplumber` (text) and `pdf2image`/Poppler (rasterization) in one Apache-licensed
  dependency. Only Tesseract itself remains an optional native dependency, same as before.
- The raw-words-with-bounding-boxes extraction model transfers directly, so the
  `bank_profiles.yaml` `pdf_table` clustering config needs no redesign.
- Consistent licensing (Apache 2.0) across PDFBox, POI and Commons CSV.
- Word-level OCR confidence is available from tess4j the same way it was from `pytesseract`,
  so `needs_review`/`confidence` flow through unchanged.

**Negative / accepted costs**

- **tess4j carries the JNI/JNA native-loading risk ADR-0001 already flagged**: it still
  requires the native `libtesseract` (+ `leptonica`) binaries per OS, and JNI/JNA loading
  failures produce noisier errors than a plain missing-executable check. This was called out
  in ADR-0001 as "strictly more portability surface than `pytesseract`" and is accepted here,
  unmitigated, as the cost of staying on the JVM. `PARSE-202`'s install hint should name the
  OS-specific native library requirement, not just "install Tesseract."
- Table reconstruction is still hand-written clustering code, same as `pdfplumber` required —
  no reduction in that maintenance surface from switching language.
- `PDFTextStripper` subclassing for word positions is a slightly lower-level API than
  `pdfplumber`'s `.extract_words()`; expect more boilerplate for the same capability.
- Two extraction paths to test, same as before. Fixture PDFs of both kinds (carried over from
  the Python plan) remain the mitigation.

## Verification

- A fixture text-based PDF (the shipped `generic_pdf_indian_savings` layout) extracts the
  same row count and field values via `PDFTextStripper`-based clustering as the Python
  `pdfplumber` path would have.
- A fixture scanned PDF yields rows with `confidence < 1.0` via tess4j; rows under
  `ocr.confidence_threshold` carry `needs_review = true`.
- With the native Tesseract library absent, a scanned-PDF run completes with exit code 0, the
  file excluded and named with `PARSE-202`, and no stack trace surfaced to a non-`--verbose`
  user.
- An encrypted fixture PDF prompts once, and the password never appears in `run-log.txt`
  (§13's redaction guarantee, unchanged).
