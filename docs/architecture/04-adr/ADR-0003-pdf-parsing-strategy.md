# ADR-0003 — PDF parsing: text-layer first, OCR as an explicit, optional fallback

**Status:** Superseded (library choice only) by
[ADR-0016](ADR-0016-pdf-parsing-java-pdfbox-tess4j.md), which re-picks `pdfplumber`/
`pdf2image`/`pytesseract` as Apache PDFBox/tess4j for Java. The per-page cascade, line
reassembly, header/footer suppression and OCR-optionality below are unchanged and still
apply.
**Date:** 2026-08-26
**Superseded by:** [ADR-0016](ADR-0016-pdf-parsing-java-pdfbox-tess4j.md) (2026-08-28)

---

## Context

FR2 requires extracting transaction rows from text-based PDF statements. FR3 requires an
OCR fallback for scanned images, with low-confidence rows flagged for review. §11.1 adds
three concrete demands: try direct extraction first, support the common Indian bank layout
(Date | Narration | Withdrawal | Deposit | Balance) as the default template with additional
per-bank templates addable *via config without code changes*, and prompt once for a
password on encrypted PDFs without storing it.

Two structural facts about bank PDFs shape everything:

1. **Most are text-based.** They are generated from the bank's core system, not scanned.
   Text extraction is exact, fast and free; OCR is approximate, slow and needs a native
   binary.
2. **Tables in them are usually ruled or column-aligned but never semantically tagged.**
   There is no `<table>` in a PDF. Reconstructing rows means clustering words by position.

The complication is that "text-based" is not a property of a *document* — a statement can
have a text-based transaction table and a scanned signature page, or the reverse.

## Decision

**A per-page cascade: `pdfplumber` table extraction first; OCR only for pages with no
usable text layer; and OCR is optional software whose absence degrades FR3 rather than
failing the run.**

The algorithm, per file:

1. **Decryption.** If encrypted, call the password prompt **once**. In-memory only, never
   logged, never persisted (§13). Refusal or wrong password ⇒ `PARSE-201`, file excluded,
   run continues (FR5).
2. **Per-page classification.** A page has a usable text layer if it yields
   ≥ `min_chars_per_page` (default 40) extractable characters. Classification is
   per-page, not per-document, so mixed PDFs work.
3. **Text path (FR2).** For each text page, run `pdfplumber.extract_table()` using the
   resolved profile's `pdf_table` settings (`strategy`, `x_tolerance`, `header_row_contains`,
   crop bounds). Emit `RawRow`s with `confidence = 1.0` and `extractor = "pdf_text"`.
4. **Line reassembly (EC2).** A row whose date and amount cells are both empty but whose
   description cell is populated is a wrapped continuation; append its text to the previous
   row's description. This happens *before* categorization, because a truncated description
   defeats keyword matching.
5. **Header/footer suppression.** Repeated header rows on pages 2+ and rows matching
   `skip_rows_matching` (e.g. `"Opening Balance"`, `"STATEMENT SUMMARY"`) are dropped.
6. **OCR path (FR3).** For image-only pages: `pdf2image` at `ocr.dpi` (default 300) →
   `pytesseract.image_to_data` for **word-level confidence** → cluster words into rows and
   columns by x/y position. Row `confidence` = mean word confidence; below
   `ocr.confidence_threshold` (default 0.75) the row is emitted with `needs_review = True`
   (FR3, NFR6).
7. **Nothing found.** If no page yields rows, the file is excluded with `PARSE-203`.

**Templates are configuration.** `bank_profiles.yaml` carries the `pdf_table` block; the
shipped `generic_pdf_indian_savings` profile is §11.1's default layout. Adding a bank is a
YAML edit (FR22, §11.1).

**Tesseract is an optional dependency.** If the binary is absent, the OCR path is skipped
entirely and scanned files are excluded with `PARSE-202` and an install hint. The tool is
fully useful without it, because most statements are text-based.

## Alternatives considered

### A. OCR everything, uniformly — **rejected**

Simpler code (one path), and immune to weird text layers. But it throws away exactness on
the ~90% case, is 20–100× slower (threatening NFR2's 30 s budget on a 5-file month), makes
Tesseract a hard install requirement (hurting NFR4/NFR5), and introduces transcription
errors into amounts — the one field where a wrong digit is silently catastrophic.

### B. `camelot` instead of `pdfplumber` — **rejected**

`camelot` is purpose-built for PDF tables and its "lattice" mode is excellent on ruled
tables. But its lattice mode depends on Ghostscript — another native binary, another NFR4
liability — and its stream mode is a less controllable heuristic. `pdfplumber` exposes raw
words with bounding boxes, which is what per-bank template tuning actually needs, and has no
native dependency. `camelot` remains a sensible per-profile alternative engine if a
specific bank's ruled tables defeat `pdfplumber`.

### C. `tabula-py` — **rejected**

Wraps `tabula-java` and therefore needs a JRE. Requiring a Java runtime inside a Python tool
that [ADR-0001](ADR-0001-language-and-runtime.md) deliberately chose over Java would be
self-defeating, and it worsens NFR4/NFR5 setup.

### D. `PyMuPDF` (fitz) — **rejected as primary, kept in reserve**

Fastest text extraction available and genuinely good. But its AGPL licence is a real
consideration for a tool that may be shared, and its table support is newer and less
battle-tested for this layout family than `pdfplumber`'s. Worth revisiting if extraction
speed ever threatens NFR2.

### E. An LLM/vision model to read statements — **rejected outright**

A hosted model would send statement contents over the network — a direct violation of NFR1,
AC8 and §13, which are the product's core promise. A *local* model would add gigabytes of
weights and non-determinism to a task where an exact table extractor is both faster and
correct. Not a trade-off worth entertaining.

## Consequences

**Positive**

- The common case (text PDFs) is exact and fast, keeping NFR2 comfortable.
- OCR cost is paid only where it is unavoidable, and its uncertainty is surfaced rather
  than hidden — `needs_review` and `confidence` flow all the way to the report (NFR6).
- No native binary is required for the default experience (NFR4, NFR5).
- Mixed text/scanned PDFs work without special-casing.
- New bank layouts are config, not code (FR22, §11.1).

**Negative / accepted costs**

- **FR3 is conditionally satisfied**, depending on a Tesseract install. This is a deliberate
  degradation, documented in [05-traceability.md](../05-traceability.md) and surfaced to the
  user with an actionable message rather than a silent failure.
- Position-based row clustering is inherently heuristic. Unusual layouts (multi-line
  addresses inside the description column, transactions spanning a page break) will need
  per-profile tuning — this is risk R1 in [06](../06-risks-and-open-questions.md), and the
  reason a config-driven template system exists at all.
- The password prompt requires a TTY. Without one, encrypted PDFs are excluded with a clear
  reason ([03](../03-interfaces-and-contracts.md) §5) rather than hanging.
- Two extraction paths to test. Mitigated by fixture PDFs of both kinds in the test suite.
