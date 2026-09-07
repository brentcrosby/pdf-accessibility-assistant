# PDF Accessibility Assistant — Decision Log

Use this log for choices that affect scope, safety, architecture, or claims. Record alternatives and evidence before marking a consequential decision accepted.

## Accepted decisions

### D-001 — Position as a remediation assistant

- **Status:** Accepted
- **Decision:** Describe the product as an assistant that can improve a PDF, not as a compliance or legal guarantee.
- **Reason:** Automated checks and limited remediation cannot establish full real-world accessibility or legal compliance.
- **Consequence:** All UI, documentation, and validation results must use bounded, accurate language.

### D-002 — Restrict document data

- **Status:** Accepted
- **Decision:** Use only public or synthetic PDFs unless workplace use has been explicitly approved.
- **Reason:** PDF contents may contain sensitive, personal, controlled, or employer-owned information.
- **Consequence:** The demo corpus and development workflow must document provenance and avoid workplace documents by default.

### D-003 — Separate automatic fixes from suggestions

- **Status:** Accepted
- **Decision:** Automatic actions are limited to a tested deterministic allowlist. Ambiguous or AI-assisted changes require explicit human approval.
- **Reason:** Model confidence is not proof of correctness, and semantic accessibility decisions require context.
- **Consequence:** Analysis, policy classification, suggestion generation, approval, and mutation remain separate concerns.

### D-004 — Preserve the original

- **Status:** Accepted
- **Decision:** Never overwrite the uploaded PDF; all changes are applied to a working copy and exported as a new file.
- **Reason:** Remediation can fail or introduce regressions, so recovery and comparison must remain possible.
- **Consequence:** Tests must confirm original immutability and change traceability.

### D-005 — Treat PAC as an external manual test

- **Status:** Accepted
- **Decision:** Use PAC, where relevant, as a documented external/manual acceptance test rather than an integrated or guaranteed validator.
- **Reason:** This keeps V1 claims aligned with the available workflow and avoids implying unsupported automation.
- **Consequence:** PAC outcomes may be recorded for fixtures but do not convert the application into a compliance guarantee.

### D-006 — Start with a modular monolith

- **Status:** Accepted for V1
- **Decision:** Build one Spring Boot application with internal component boundaries.
- **Reason:** It supports the complete workflow with less operational complexity and makes the code easier to learn and explain.
- **Consequence:** Microservices are excluded unless a later measured need justifies them.

### D-007 — Favor learning evidence over feature count

- **Status:** Accepted
- **Decision:** Use narrow vertical slices, feasibility spikes, tests, and personal explanations as progress measures.
- **Reason:** The portfolio goal includes rebuilding and demonstrating independent programming ability.
- **Consequence:** Features may be cut when they outpace understanding or lack a safe, testable path.

## Proposed decisions awaiting evidence

### D-008 — Initial deterministic allowlist

- **Status:** Proposed
- **Proposal:** Begin with document title metadata, display-title preference, and user-confirmed document language.
- **Evidence needed:** PDFBox round-trip experiments across the reference corpus, before/after inspection, and validation results.

### D-009 — veraPDF integration mode

- **Status:** Open
- **Options:** Embedded library, separate local process, or development/test-only invocation.
- **Decision criteria:** Reliability, licensing, CI portability, structured results, complexity, and explainability.

### D-010 — First ambiguous suggestion

- **Status:** Open
- **Options:** Figure alt text, heading level, or explanation-only guidance.
- **Decision criteria:** Safe object targeting, reviewability, visible portfolio value, testability, and limited document exposure.

### D-011 — AI use in V1

- **Status:** Deferred
- **Options:** Provider-backed suggestions, heuristic suggestions behind the same interface, or no external AI in the first release.
- **Decision criteria:** Data handling, cost, reliability, educational value, and whether AI materially improves the bounded workflow.

### D-012 — Approve the V1 planning boundary

- **Status:** Accepted on 2026-09-03
- **Decision:** V1 will analyze title metadata, display-title preference, document language, basic tagged/untagged state, encrypted or restricted inputs, malformed inputs, and selected unsupported conditions. Automatic fixes remain limited to metadata actions that pass the evidence-based allowlist. The only planned semantic AI demonstration is an alt-text suggestion for an existing, safely identifiable `<Figure>` tag, with explicit human approval required before mutation.
- **Deferred from V1:** Automatic full-document tagging, reading-order repair, heading mutation, table remediation, form remediation, and OCR.
- **Test strategy:** Use approved public Caltrans PDFs as realistic baselines, controlled degraded derivatives with known defects, Acrobat auto-tagged derivatives as comparison output, optional manually reviewed references, and synthetic edge-case fixtures.
- **Reason:** This boundary demonstrates an end-to-end remediation workflow while keeping semantic risk, technical complexity, data exposure, and learning scope manageable.
- **Consequence:** Feasibility spikes may remove a candidate capability, but adding broader remediation requires a new recorded decision. Acrobat, veraPDF, and PAC results are evidence sources rather than guarantees or semantic ground truth.

### D-013 — Fixture storage and reuse policy

- **Status:** Accepted on 2026-09-07
- **Decision:** Keep public-source PDF bytes and all derivatives out of GitHub until each source has completed file-specific reuse and third-party-content review. The repository may contain source URLs, retrieval dates, SHA-256 hashes, inspection results, synthetic fixtures, and reproducible defect specifications. Temporary local copies may be used for read-only inspection and later approved feasibility spikes, but are not project artifacts to commit.
- **Reason:** Public availability does not automatically establish redistribution rights, especially for embedded visual or third-party material. This policy keeps the portfolio repository reviewable while preserving the project's public-or-synthetic data boundary.
- **Consequence:** CT-001 through CT-003 remain reference-only baselines in GitHub. The first controlled defect profile is a written specification, not a created or committed PDF. Fully synthetic fixtures remain the default choice when a shareable binary is needed.

## Assumptions

- Decisions marked “accepted for V1” may be revisited after evidence changes.
- Technical spikes will provide the missing evidence for D-008 through D-010.
- The log will be updated in the same change that adopts a consequential decision.
- D-012 approves the planning boundary; it does not claim that any candidate capability has passed implementation or validation.

## Non-goals

- Logging trivial formatting or naming choices.
- Treating proposed ideas as commitments.
- Rewriting earlier entries to hide changed thinking; superseded decisions should remain visible.

## Open questions

- Who approves a decision change for this single-owner project: the owner alone, or the owner after a written review checklist?
- Should each accepted technical decision link to a test, experiment, or issue once implementation begins?
- What threshold of evidence is required to add an action to the automatic-fix allowlist?
- When, if ever, will a reviewed public-source derivative be retained outside temporary local analysis storage?

## Deferred decisions

- Deployment, authentication, persistence, AI provider, and advanced remediation.
- Exact UI framework and package structure.
- Stretch goals and post-V1 roadmap.
- Whether any approved public-source PDF bytes or derivatives will be versioned in the repository after review.

## Recommended next planning step

Create a **V1 issue-and-remediation matrix** listing each candidate check, its evidence source, classification (automatic, review-required, report-only, or unsupported), intended mutation, validation method, risks, and the experiment needed before it enters scope. Do not scaffold the application until that matrix narrows the first feasibility spikes.
