# PDF Accessibility Assistant — Architecture Notes

## Architectural direction

Use a modular Spring Boot application with clear boundaries between file intake, analysis, deterministic remediation, suggestions, human decisions, PDF mutation, and validation. Begin as one deployable application; avoid microservices unless a demonstrated constraint later requires separation.

## Intended stack

- Java 21
- Spring Boot and Maven
- Spring Web
- Bean Validation
- Apache PDFBox
- veraPDF where practical
- JUnit 5 and Mockito
- Git and GitHub Actions

## Proposed logical components

| Component | Responsibility | Boundary to preserve |
| --- | --- | --- |
| Upload intake | Validate request metadata, file type, size, and processing eligibility. | Never treat a filename or MIME claim alone as proof of a valid PDF. |
| PDF workspace | Hold the original and working copy for one processing session. | Original is immutable; retention is temporary and explicit. |
| Analyzer | Run supported deterministic checks and normalize findings. | Analysis does not mutate the PDF. |
| Remediation policy | Classify actions as automatic, review-required, report-only, or unsupported. | AI output cannot promote an action into the automatic category. |
| Deterministic fixer | Apply only allowlisted, tested metadata changes. | Reject any unregistered mutation type. |
| Suggestion engine | Produce bounded proposed changes plus rationale and uncertainty. | Produces proposals, never direct mutations. |
| Review workflow | Record approval or rejection for each proposal. | Only approved proposals may reach a capable mutator. |
| PDF writer | Apply authorized changes to a working copy and export a new file. | Preserve an auditable before/after change record. |
| Validator | Re-run internal checks and veraPDF checks where feasible. | Report tool scope and limitations; do not convert results into a compliance guarantee. |

## Core domain concepts

- **Finding:** an observed condition with evidence, source check, severity, and support status.
- **Remediation action:** a typed proposed change classified by policy.
- **Automatic fix:** an allowlisted deterministic action with validated inputs.
- **Suggestion:** a review-required action with rationale and uncertainty.
- **Review decision:** approval or rejection tied to a particular suggestion version.
- **Change record:** before/after values, action source, decision source, and outcome.
- **Validation result:** a check result plus tool, profile, time, and limitations.

## Processing sequence

The planned sequence is intake → immutable original → analysis → policy classification → deterministic fixes and/or suggestions → human decisions → authorized mutation of a working copy → export → revalidation. Failures should preserve the original and provide a useful partial report when safe.

## Safety, privacy, and integrity

- Accept only public or synthetic PDFs unless workplace use has been explicitly approved.
- Reject or isolate malformed, encrypted, oversized, or unsupported inputs according to documented policy.
- Use temporary storage and delete session files according to a defined retention rule.
- Do not send document content to an external AI service until the provider, disclosure, data handling, and user consent model are explicitly decided.
- Record every applied change; never silently mutate a document.
- Avoid logging document contents or extracted text by default.

## Testing strategy notes

- Unit-test classification policy and each deterministic mutation.
- Use synthetic fixture PDFs for expected before/after behavior.
- Add regression fixtures for malformed, encrypted, tagged, and untagged cases.
- Verify originals are byte-for-byte unchanged after every workflow.
- Use integration tests for upload-to-export paths and veraPDF integration where feasible.
- Keep PAC results as documented manual acceptance evidence for selected fixtures.

## Assumptions

- A modular monolith is sufficient for V1.
- Temporary local/session storage is acceptable for the portfolio demo.
- PDFBox can handle the selected metadata and limited structure operations.
- External validation can be represented through an adapter so veraPDF integration choices remain reversible.

## Non-goals

- Production-scale distributed processing or microservices.
- Long-term document storage, document search, or user libraries.
- A general-purpose PDF editor.
- An architecture that depends on an AI provider before the deterministic workflow works.

## Open questions

- Can PDFBox safely write the chosen structure-level suggestions without corrupting tags or content?
- Which veraPDF integration mode is simplest and most portable for CI?
- Should suggestion generation initially use rules, an AI model, or both behind one interface?
- What information must be retained to make changes reproducible without retaining document content?
- What failure modes require report-only behavior instead of remediation?

## Deferred decisions

- Package structure and concrete class/API names.
- Database choice; V1 may not require a database.
- Front-end approach beyond a minimal accessible interface.
- External model provider and production deployment topology.
- Containerization unless needed for reliable veraPDF execution.
