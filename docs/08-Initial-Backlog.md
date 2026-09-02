# PDF Accessibility Assistant — Initial Backlog

This backlog stops before production application scaffolding. Each issue should produce a small artifact, experiment result, or decision that another person can review.

## Milestone 1 — V1 Planning

### 1. Review and finalize the V1 issue/remediation matrix

**Labels:** `planning`, `v1`, `decision-needed`

**Outcome:** Each candidate is classified as automatic candidate, review-required candidate, report-only, or unsupported/deferred.

**Done when:** Open questions that block the first spikes are listed, and no candidate is described as implemented.

### 2. Define the approved public/synthetic PDF corpus

**Labels:** `planning`, `accessibility`, `v1`

**Outcome:** A corpus manifest describes each permitted fixture, its provenance, intended condition, and expected use.

**Done when:** The corpus covers title, display-title preference, language, tagged/untagged, encrypted/restricted, and malformed cases without workplace or sensitive documents.

### 3. Study and document the relevant PDF concepts

**Labels:** `learning`, `documentation`, `accessibility`

**Outcome:** A short owner-written note explains the information dictionary, XMP metadata, catalog, viewer preferences, document language, mark information, and structure tree.

**Done when:** The owner can explain how the concepts differ and which V1 checks depend on each one.

### 4. Define manual PAC acceptance steps

**Labels:** `planning`, `testing`, `accessibility`

**Outcome:** A repeatable checklist identifies which selected exported fixtures will be checked in PAC and how results will be recorded.

**Done when:** The checklist explicitly states that PAC is an external/manual acceptance test and not a compliance guarantee.

## Milestone 2 — Feasibility Spikes

### 5. Spike PDFBox document inspection

**Labels:** `technical-spike`, `learning`, `v1`

**Outcome:** A disposable experiment reports the selected metadata/catalog properties for representative fixtures.

**Done when:** Results and failure cases are documented without creating the production application structure.

### 6. Test document-title metadata round trips

**Labels:** `technical-spike`, `testing`, `accessibility`

**Outcome:** Evidence shows how title values are read, written, saved, reopened, and preserved across representative fixtures.

**Done when:** Risks involving the information dictionary and XMP are recorded and D-008 can be refined.

### 7. Test display-title preference changes

**Labels:** `technical-spike`, `testing`, `accessibility`

**Outcome:** Evidence establishes the exact preconditions and PDFBox behavior for the preference.

**Done when:** Save/reopen results support either allowlisting or rejecting the candidate.

### 8. Test document-language changes

**Labels:** `technical-spike`, `testing`, `accessibility`, `decision-needed`

**Outcome:** Evidence establishes accepted language-value inputs, multilingual limitations, and safe write behavior.

**Done when:** The team decides whether language is a V1 review-required change or report-only finding.

### 9. Evaluate veraPDF integration modes

**Labels:** `technical-spike`, `testing`, `decision-needed`

**Outcome:** Compare embedded library, separate process, and development/test-only options.

**Done when:** D-009 records a recommendation based on reliability, licensing, CI portability, structured output, and complexity.

### 10. Define behavior for encrypted, malformed, tagged, and untagged PDFs

**Labels:** `technical-spike`, `testing`, `accessibility`, `v1`

**Outcome:** A safe behavior table defines reject, stop, limited-analysis, and report-only cases.

**Done when:** The original is preserved, errors are useful, and no logs require document content.

### 11. Decide the deterministic-fix allowlist

**Labels:** `planning`, `decision-needed`, `v1`

**Blocked by:** Issues 5–10.

**Outcome:** D-008 is accepted, revised, or rejected using linked experiment evidence.

**Done when:** Every automatic action passes the matrix promotion checklist.

### 12. Select the first human-review suggestion

**Labels:** `planning`, `decision-needed`, `v1`

**Outcome:** Choose alt text, heading level, explanation-only guidance, or no structure-level suggestion for the first release.

**Done when:** D-010 records object-targeting feasibility, mutation safety, testability, data exposure, and portfolio value.

### 13. Design the deterministic vertical slice

**Labels:** `planning`, `documentation`, `v1`

**Blocked by:** Issues 11 and 12.

**Outcome:** A small design describes upload → analyze → one safe fix → export → revalidate, with failure behavior and tests.

**Done when:** The owner can trace and explain the complete flow before Spring Boot scaffolding begins.

## Suggested labels

| Label | Purpose |
| --- | --- |
| `planning` | Scope, design, or organizational work |
| `learning` | Work whose primary outcome is understanding |
| `technical-spike` | Disposable experiment that answers a bounded question |
| `v1` | Required or considered for V1 |
| `testing` | Automated or manual verification work |
| `documentation` | Project or learning documentation |
| `accessibility` | Accessibility analysis, remediation, or validation |
| `blocked` | Cannot proceed until a named dependency is resolved |
| `decision-needed` | Requires a recorded choice before proceeding |

## Suggested milestones

1. V1 Planning
2. Feasibility Spikes
3. Deterministic Vertical Slice
4. Human Review Workflow
5. V1 Portfolio Release
