# CT-001 Metadata Defect Profile

## Status and purpose

- **Status:** Defined on 2026-09-07; not created
- **Baseline:** CT-001, *Frequently Asked Questions about Caltrans ADA Infrastructure Program*
- **Baseline SHA-256:** `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76`
- **Purpose:** Provide small, known metadata cases for the first PDFBox feasibility spikes. This profile does not authorize a production feature or claim accessibility compliance.

The three cases below must be created and evaluated separately. Do not bundle them into one PDF, because each check needs an unambiguous expected result.

## Baseline expectations

The inspected CT-001 baseline is a six-page, unencrypted PDF with a tagged structure tree. Its information-dictionary title is populated, catalog language is `en`, and the viewer preference to display the document title is enabled.

These properties are baseline observations only. They are not a finding that the PDF is fully accessible or compliant.

## Planned cases

| Fixture ID | Intended single defect | Expected analysis result | Planned V1 disposition |
| --- | --- | --- | --- |
| CT-001-MD-001 | Remove the document title consistently from relevant title metadata representations discovered in the later PDFBox spike. Do not leave conflicting title values. | Missing title is reported; the workflow requests a human-provided or confirmed title. | Review-required input; safe synchronization is an automatic-action candidate only after evidence. |
| CT-001-MD-002 | Set the catalog viewer preference to not display the document title, while preserving the baseline title and language. | Disabled display-title preference is reported. | Automatic-action candidate only when a valid confirmed title exists. |
| CT-001-MD-003 | Remove the catalog document-language value, while preserving the baseline title and display-title preference. | Missing language is reported; the workflow requests a human-confirmed language. | Review-required input; writing the confirmed value requires feasibility evidence. |

## Invariants for every planned derivative

- Preserve the baseline PDF as-is and write a separately named working copy.
- Change only the named metadata target for that case.
- Preserve page count, encryption state, visible page content, annotations, structure tree, and non-target metadata within the documented save-tool limits.
- Do not modify tags, reading order, headings, links, figures, forms, or text content.
- Record the source hash, derivative hash, tool name and version, exact operation, timestamp, and resulting metadata snapshot.
- Keep the derivative in local temporary analysis storage unless a later reuse decision explicitly permits retention or distribution.

## Required evidence before creation

- [x] A read-only PDFBox 3.0.8 inspection spike identified the information-dictionary title, catalog language, viewer preference, and XMP presence in CT-001 on 2026-09-07. Exported XMP contains the title in `dc:title` with `xml:lang="x-default"`.
- A written transformation plan names the tool version and expected before/after values.
- Save/reopen checks are designed before the mutation is run.
- The activity remains a feasibility experiment, outside the production application and without Spring Boot scaffolding.

## Planned verification

For each derivative, compare it with the recorded baseline after reopening it with the chosen library and an independent inspection tool where practical:

1. Confirm the intended single metadata difference.
2. Confirm expected invariants, including page count, unencrypted state, tagged/structure signals, and unchanged non-target title/language/viewer-preference values.
3. Inspect at least the first rendered page for unintended visible changes.
4. Record any save-time side effects, especially metadata synchronization or loss.
5. Do not treat a successful read, render, veraPDF result, or later PAC 3 result as a compliance guarantee.

## Boundaries and deferred work

- This profile does not create, commit, distribute, or auto-tag a PDF.
- CT-002 figure-alt-text work is intentionally excluded because its current figure tags appear to cover logos, not the visible data graphics.
- Tag stripping, Acrobat auto-tagging, malformed/encrypted synthetic fixtures, veraPDF evaluation, and manual PAC 3 acceptance are separate future planning or spike tasks.

## Recommended next step

Write a precise PDFBox title-metadata transformation plan for CT-001-MD-001, including how the information dictionary and XMP will be changed consistently, then review it before any derivative is created.
