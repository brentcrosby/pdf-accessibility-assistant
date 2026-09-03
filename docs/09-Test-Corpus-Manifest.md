# PDF Accessibility Assistant — Test Corpus Manifest

## Status

The owner approved all three public Caltrans documents for baseline inspection on **2026-09-03**. Temporary local copies were downloaded for read-only analysis; PDF bytes and derived files have not been added to the repository.

## Handling rules

- Use only publicly available Caltrans documents or fully synthetic PDFs.
- Record the source URL, retrieval date, SHA-256 hash, tool versions, transformations, and expected findings before using a fixture.
- Review each source for file-specific copyright notices and third-party content before committing or redistributing its bytes.
- Until that review is complete, keep only the source reference and reproducible transformation plan in the repository.
- Label every derivative as an experimental test fixture, not an official Caltrans publication.
- Treat Acrobat auto-tagged output, veraPDF results, and PAC results as evidence sources rather than semantic ground truth or compliance guarantees.
- Do not use internal, unpublished, confidential, personal, controlled, or other workplace documents without explicit workplace approval.

## Baseline inspection method

- **Retrieved:** 2026-09-03 from the source URLs below
- **Tools:** Poppler `pdfinfo` 26.05.0, `pypdf` 6.10.0, and representative-page visual renders
- **Scope:** Read-only metadata, catalog, structure-tree, form, text-extraction, and visual checks
- **Limits:** These observations are not PAC, PDF/UA, WCAG, or legal-compliance results. Role counts show structure-tree presence, not semantic correctness.

Caltrans states that information on its website is generally considered public domain unless otherwise indicated, while warning that some third-party material may require separate permission. See the [Caltrans Conditions of Use](https://dot.ca.gov/conditions-of-use).

## Candidate public sources

### CT-001 — Text-heavy baseline

- **Document:** [Frequently Asked Questions about Caltrans ADA Infrastructure Program](https://dot.ca.gov/-/media/dot-media/programs/local-assistance/documents/ada/2022/faqs-caltrans-ada-infrastructure-program.pdf)
- **Observed length:** 6 pages
- **Source type:** Public Caltrans PDF
- **Selection status:** Approved for baseline inspection
- **Why it is useful:** Primarily sequential question-and-answer text with numbered questions, lists, links, contact information, and multi-page reading flow. It provides a manageable baseline for metadata and basic tag inspection.
- **Primary planned checks:** Title metadata, display-title preference, document language, tagged/untagged state, heading/paragraph/list observations, link observations, and text extraction order.
- **Initial controlled derivatives:** Metadata-defect version and fully untagged version.
- **V1 expectation:** Metadata candidates may be remediated after successful spikes. Structural observations remain report-only unless separately approved.
- **Retrieved:** 2026-09-03; temporary local analysis copy only
- **SHA-256:** `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76`
- **Baseline observations:** 6 pages; unencrypted; tagged flag and structure tree present; title present; language `en`; display-document-title enabled; no figure roles found. The catalog contains an AcroForm dictionary but no top-level fields, so it is not an interactive-form test case.
- **Reuse review:** Pending file-specific review

### CT-002 — Figures and visual-layout baseline

- **Document:** [The Traffic Safety Navigator — Summer 2025](https://dot.ca.gov/-/media/dot-media/programs/safety-programs/documents/shsp/newsletters/2025-shsp-e-newsletter-summer-v06-a11y.pdf)
- **Observed length:** 9 pages
- **Source type:** Public Caltrans PDF
- **Selection status:** Approved for baseline inspection
- **Why it is useful:** A visually designed newsletter containing charts, statistics, graphic groupings, lists, headings, and dense page layouts. It is the strongest candidate for examining figure tags and alt-text behavior.
- **Primary planned checks:** Metadata, tagged/untagged state, figure identification, existing alt text, heading observations, extracted reading sequence, and automated-tagging comparison.
- **Initial controlled derivatives:** One known figure-alt-text defect, metadata-defect version, fully untagged version, and Acrobat auto-tagged derivative.
- **Optional reference:** A manually reviewed version for semantic and PAC comparison.
- **V1 expectation:** The only planned semantic mutation candidate is an approved alt-text suggestion attached to an existing, safely identifiable `<Figure>` tag. Reading-order and complex-layout findings remain report-only.
- **Retrieved:** 2026-09-03; temporary local analysis copy only
- **SHA-256:** `89ee1d6775143a90b2b2a0303f9b6f198b2ffea46ca4988a065420012560869b`
- **Baseline observations:** 9 pages; unencrypted; tagged flag and structure tree present; title present; language `en-US`; display-document-title enabled. Two `<Figure>` roles have alt text, but their strings appear to describe logos rather than the page-3 data graphics. This makes the document useful for detecting the gap between visible graphics and existing figure tags, but suitability for the V1 alt-text mutation experiment remains open.
- **Reuse review:** Pending file-specific and third-party visual-content review

### CT-003 — Complex/form-like baseline

- **Document:** [Sustainable Transportation Planning Grant Program — Strategic Partnerships Grant Application Narrative](https://dot.ca.gov/-/media/dot-media/programs/transportation-planning/documents/division-transportation-planning/regional-and-community-planning/sustainable-transportation-planning-grants/6-sp-app-narrative-form-v2-a11y.pdf)
- **Observed length:** 6 pages
- **Source type:** Public Caltrans PDF
- **Selection status:** Approved for baseline inspection
- **Why it is useful:** Repeated headers and footers, structured prompts, lists, response areas, and form-like page organization provide a realistic difficult case without requiring a very large manual.
- **Primary planned checks:** Metadata, tagged/untagged state, repeated-content observations, list and heading observations, extraction order, and unsupported-complexity reporting.
- **Initial controlled derivatives:** Metadata-defect version, fully untagged version, and Acrobat auto-tagged derivative if technically appropriate.
- **V1 expectation:** Complex structure, form remediation, tables, and reading-order repair remain report-only or unsupported.
- **Retrieved:** 2026-09-03; temporary local analysis copy only
- **SHA-256:** `68ab060d4ec347514885d00e5851720d2fa06d22d0af58358bdfa908a2141c96`
- **Baseline observations:** 6 pages; unencrypted; tagged flag and structure tree present; title present; language `en-US`; display-document-title enabled; one logo figure has alt text. The layout is form-like, but no AcroForm is present; the response areas are static page content.
- **Reuse review:** Pending file-specific review

## Coverage summary

| Fixture | Main role | Automatic-fix candidates | Human-review candidate | Report-only observations |
| --- | --- | --- | --- | --- |
| CT-001 | Straightforward text baseline | Title, display-title preference, confirmed language | None initially | Tags, lists, links, reading sequence |
| CT-002 | Figures and visual layout | Title, display-title preference, confirmed language | Alt text for one existing identifiable figure tag | Headings, reading order, complex layout |
| CT-003 | Complex/form-like layout | Title, display-title preference, confirmed language | None initially | Repeated content, lists, tables/forms, reading order |

## Required synthetic fixtures

Public documents do not replace controlled synthetic edge cases. Create synthetic fixtures later for:

- Encrypted or permission-restricted input.
- Malformed or unsupported input.
- Minimal tagged and untagged PDFs.
- Conflicting title values between the information dictionary and XMP.
- Missing and invalid language values.

## Current gate

Before creating or retaining any transformed fixture:

- [x] Owner approved CT-001, CT-002, and CT-003 for baseline inspection on 2026-09-03.
- [x] Original files were downloaded only to temporary, untracked analysis storage.
- [x] Baseline hashes and technical observations were recorded.
- [ ] File-specific reuse and third-party-content review is complete.
- [ ] Local fixture storage and repository rules are agreed.
- [ ] The first transformation is limited to a written defect profile.
- [ ] No production application scaffolding begins as part of corpus preparation.

## Open questions and deferred decisions

- Decide whether repository fixtures should contain PDF bytes, reproducible transformation scripts, hashes plus source URLs, or only fully synthetic PDFs.
- Confirm whether CT-002 has an existing safely identifiable non-logo figure suitable for the V1 alt-text approval experiment; current structure-tree evidence does not establish one.
- Defer Acrobat auto-tagging, tag stripping, controlled defect creation, veraPDF runs, and manual PAC 3 testing until the storage/reuse rules and written defect profiles are approved.
