# PDF Accessibility Assistant — Project Brief

## Purpose

Build a portfolio-quality web application that assists a human in finding and remediating basic PDF accessibility problems. The product should make safe, explainable corrections automatically and present uncertain corrections for explicit human approval.

## Product position

This is a **remediation assistant**, not a compliance guarantee. Its output may improve accessibility, but it must not claim to guarantee PAC, PDF/UA, WCAG, Section 508, or legal compliance. Final judgment remains with a qualified human and the applicable validation process.

## Intended user and core problem

The initial user is someone who understands basic document accessibility but wants a clearer and faster workflow for:

1. Inspecting a PDF for a limited set of accessibility issues.
2. Applying high-confidence metadata fixes.
3. Reviewing suggestions for changes that require context or judgment.
4. Exporting and revalidating the resulting PDF.

## V1 outcome

A user can upload an allowed PDF, receive a structured issue report, apply deterministic metadata fixes, approve or reject human-review suggestions, export a remediated copy, and see revalidation results. PAC may be used afterward as an external/manual acceptance test.

## Portfolio and learning goals

- Rebuild and demonstrate independent Java programming ability.
- Show a complete Spring Boot workflow rather than a collection of disconnected demos.
- Practice PDF parsing, validation, domain modeling, testing, Git discipline, and CI.
- Demonstrate judgment about AI boundaries, explainability, privacy, and human review.
- Keep a development journal showing what was understood, attempted, tested, and revised.

## Success signals

- The V1 workflow works end to end on a small, documented test corpus.
- Automatic changes are restricted to an explicit deterministic allowlist.
- Every proposed or applied change is visible and explainable to the user.
- Revalidation distinguishes improvements, remaining issues, and tool limitations.
- Automated tests cover the highest-risk document-processing behavior.
- The repository documentation explains scope, tradeoffs, and known limitations honestly.

## Assumptions

- V1 is a single-user portfolio demonstration, not a production document service.
- Only public or synthetic PDFs will be used unless workplace use is explicitly approved.
- veraPDF can provide useful machine-readable validation where practical, but integration feasibility must be confirmed with a spike.
- Ambiguous structural or semantic remediation cannot be safely automated without review.

## Non-goals

- Guaranteed accessibility or legal compliance.
- Full PDF/UA remediation.
- Replacing expert review, PAC, assistive-technology testing, or organizational QA.
- Processing confidential, personal, controlled, or workplace documents without approval.
- Multi-user accounts, billing, production-scale storage, or enterprise deployment in V1.
- Solving arbitrary reading-order, table, form, OCR, or complex tagging problems in V1.

## Open questions

- Which two or three ambiguous suggestion types are feasible enough to demonstrate in V1?
- What exact deterministic metadata fields can PDFBox update without damaging representative files?
- Will veraPDF run as an embedded library, a separate process, or only in the development/test workflow?
- What file-size and page-count limits are appropriate for the demo?
- Is any AI provider needed for V1, or should the first suggestion engine be heuristic and provider-neutral?

## Deferred decisions

- Deployment platform and production hosting.
- Authentication and persistent user accounts.
- Choice of AI model or vendor.
- Long-term storage and retention policy beyond temporary processing.
- Advanced remediation for tables, forms, OCR, mathematical content, and complex reading order.
