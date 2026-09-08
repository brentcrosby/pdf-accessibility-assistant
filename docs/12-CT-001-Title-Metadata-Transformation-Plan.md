# CT-001 Title-Metadata Transformation Plan

## Status and purpose

- **Status:** Planned on 2026-09-08; no derivative has been created.
- **Input:** CT-001 baseline listed in the test corpus manifest.
- **Baseline SHA-256:** `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76`
- **Purpose:** Establish whether a title can be removed and later restored consistently across the PDF information dictionary and XMP `dc:title` without unintended save-time changes.

This is a temporary PDFBox feasibility experiment, not a production mutation, compliance test, or PAC result.

## Preconditions

- Use only the approved public CT-001 reference in local temporary storage; do not alter it or commit either it or a derivative.
- Verify the source SHA-256 matches the recorded baseline before every run.
- Use PDFBox 3.0.8 and Java 21 for the experiment.
- Work from copied input bytes and write a separately named temporary derivative.
- Record the exact title used in the experiment. Use a neutral test value such as `CT-001 title round-trip test` rather than a guessed production title.

## Baseline snapshot to capture before mutation

| Property | Expected baseline value |
| --- | --- |
| Page count | 6 |
| Encryption | false |
| Information-dictionary title | `Frequently Asked Questions about Caltrans ADA Infrastructure Program` |
| XMP title | same title in `dc:title` with `xml:lang="x-default"` |
| Catalog language | `en` |
| `DisplayDocTitle` | true |
| Mark information | present; `Marked=true` |
| Structure tree | present |

## Planned two-stage experiment

### A. Create the missing-title derivative

1. Copy the baseline bytes to a temporary working file.
2. Load the copy with PDFBox.
3. Clear the information-dictionary title.
4. Clear the XMP `dc:title` entry, including its `x-default` value, using the supported XMP API selected for the spike.
5. Save to a new temporary file; do not overwrite either the baseline or the input copy.
6. Reopen the saved derivative and verify that both title representations are absent and that all baseline invariants except the intended title change remain true.

### B. Restore a reviewed title

1. Load the Stage A derivative as a new input.
2. Set the same agreed test title in the information dictionary and the XMP `dc:title` `x-default` entry.
3. Save to a second temporary file.
4. Reopen it and verify that both title representations contain the same exact value.
5. Confirm the Stage B file remains a new derivative; no prior input bytes may be changed.

## Save/reopen assertions

For both stages, after reopening:

- Assert the intended title state in both the information dictionary and XMP.
- Assert page count remains 6 and encryption remains false.
- Assert catalog language remains `en` and `DisplayDocTitle` remains true.
- Assert mark information and the structure-tree root are still present.
- Compare non-target metadata fields captured in the baseline snapshot and record any library normalization.
- Render at least page 1 before and after; inspect for visible-content changes.
- Compute and record SHA-256 values for all temporary derivatives.

## Failure and rollback handling

- Stop the experiment if XMP cannot be updated, values disagree after reopening, page/encryption/tagging invariants change, or rendering shows an unintended visual change.
- Record the observed behavior and leave title mutation out of the automatic allowlist.
- Delete temporary derivatives after recording results unless a later explicit retention decision is made.

## Decision gate

Title writing may be proposed as a **review-required metadata action** only if both stages pass and the decision log records the evidence. It must remain unavailable as an automatic action until a later, separate decision confirms the appropriate confidence level and validation boundary.
