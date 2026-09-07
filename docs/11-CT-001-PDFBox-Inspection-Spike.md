# CT-001 PDFBox Inspection Spike

## Purpose and boundary

This disposable experiment tested whether PDFBox can read the metadata and catalog fields needed for the first V1 metadata cases. It did not alter, save, export, or create a project PDF derivative. It is evidence for planning, not a production implementation or an accessibility-compliance result.

## Environment

| Item | Value |
| --- | --- |
| Baseline | CT-001 — *Frequently Asked Questions about Caltrans ADA Infrastructure Program* |
| Baseline SHA-256 before and after | `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76` |
| PDFBox | 3.0.8 standalone application package |
| Java runtime used for this disposable experiment | OpenJDK 17.0.20 |
| Intended application baseline | Java 21; not tested in this spike |
| Operations | Read-only PDFBox load, catalog inspection, XMP export, text export |

## Results

| Field or signal | PDFBox result | Planning implication |
| --- | --- | --- |
| Page count | `6` | Baseline invariant for future save/reopen checks |
| Encryption | `false` | CT-001 is usable for the metadata spike |
| Information-dictionary title | `Frequently Asked Questions about Caltrans ADA Infrastructure Program` | A missing-title case must remove or replace this value intentionally |
| Catalog language | `en` | Language is a catalog-level candidate, separate from title metadata |
| Viewer preference: `DisplayDocTitle` | `true` | A single-preference defect can be isolated while retaining a valid title |
| XMP stream | Present | Title handling cannot assume the information dictionary is the only representation |
| XMP title | `dc:title` has the same title with `xml:lang="x-default"` | CT-001-MD-001 must define consistent information-dictionary and XMP handling |
| Mark information | Present; `Marked=true` | A tagged signal exists but does not establish semantic correctness |
| Structure tree | Present | Preserve as a non-target invariant in metadata experiments |

## What this establishes

- PDFBox can load CT-001 and read the planned metadata/catalog signals in this temporary environment.
- CT-001 contains both an information-dictionary title and an XMP `dc:title` representation.
- The three metadata defects in the CT-001 profile can be designed as isolated cases.

## What this does not establish

- Whether PDFBox 3.0.8 writes title values consistently to the information dictionary and XMP.
- Whether a save/reopen preserves all non-target metadata, tags, and visible content.
- Whether PDFBox behaves the same under the intended Java 21 toolchain.
- That any metadata action is approved for the automatic-fix allowlist.
- PAC, PDF/UA, WCAG, or legal compliance.

## Next evidence gate

Before creating CT-001-MD-001, write a transformation plan that names the PDFBox APIs, source and target metadata values, tool version, save/reopen assertions, and rollback handling. Run it only as a temporary feasibility experiment after review. The display-title and language cases require their own similarly bounded plans.
