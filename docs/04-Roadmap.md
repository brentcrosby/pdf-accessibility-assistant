# PDF Accessibility Assistant — Learning-Oriented Roadmap

## Working principle

Each phase should produce evidence of understanding: short design notes, small experiments, tests, and a retrospective. AI may assist, but the owner should be able to explain and independently modify every accepted design and implementation choice.

## Phase 0 — Planning and boundaries

**Goal:** Make the project small, safe, and testable before coding.

- Finalize the V1 issue and remediation matrix.
- Define the allowed document policy and disclaimer language.
- Choose a small public/synthetic reference corpus.
- Write workflow acceptance criteria and manual PAC test steps.
- Create a question log for unfamiliar PDF concepts.

**Learning evidence:** Explain PDF metadata versus logical structure, deterministic versus ambiguous remediation, and what veraPDF and PAC can and cannot establish.

**Exit:** V1 scope and the first experiment backlog are approved.

## Phase 1 — Technical feasibility spikes

**Goal:** Retire the largest uncertainties with throwaway experiments, not production scaffolding.

- Inspect representative PDFs with PDFBox.
- Test safe round trips for title, display-title preference, and language metadata.
- Explore tagged structure access without promising mutation support.
- Confirm a viable veraPDF invocation and machine-readable result path.
- Record corruption risks, unsupported files, and observed limitations.

**Learning evidence:** Reproduce each experiment without generated production code and explain the relevant PDF objects and failure modes.

**Exit:** The deterministic allowlist and suggestion shortlist are evidence-based.

## Phase 2 — Deterministic vertical slice

**Goal:** Complete upload → analyze → safe metadata fix → export → revalidate for one narrow case.

- Establish the Spring Boot project only after Phase 1 decisions are recorded.
- Implement intake validation and immutable-original handling.
- Normalize findings and apply one allowlisted fix.
- Export a new PDF with a change record.
- Add unit and integration tests plus CI.

**Learning evidence:** Implement at least one core path personally from a written design, then use review tools to critique it. Be able to trace the request through every layer.

**Exit:** One end-to-end path is tested and explainable.

## Phase 3 — Complete deterministic V1 foundation

**Goal:** Add the remaining approved metadata checks and fixes without broadening scope.

- Expand the allowlist one tested action at a time.
- Add unsupported/encrypted/malformed file behavior.
- Improve finding explanations and change summaries.
- Expand the fixture matrix and regression coverage.

**Learning evidence:** For each added action, document the input contract, mutation, verification, and failure behavior.

**Exit:** Deterministic acceptance criteria pass on the reference corpus.

## Phase 4 — Human-review suggestion loop

**Goal:** Demonstrate one bounded ambiguous remediation with explicit approval.

- Implement versioned suggestions with rationale and uncertainty.
- Add approve/reject behavior and ensure rejected items cannot mutate output.
- Apply only approved suggestions that have a technically safe mutation path.
- Keep provider-specific AI logic behind a replaceable boundary, if AI is used.

**Learning evidence:** Explain why the selected task requires judgment and how the architecture prevents unreviewed AI changes.

**Exit:** The approval loop is testable, auditable, and cannot bypass policy.

## Phase 5 — Validation, accessibility, and portfolio polish

**Goal:** Make the project credible, demonstrable, and honest about limits.

- Complete supported revalidation reporting.
- Run documented PAC manual acceptance tests on selected fixtures.
- Test the web interface for keyboard and screen-reader basics.
- Write the README, architecture summary, demo script, and limitations.
- Capture decisions and learning reflections; remove unsupported claims.

**Learning evidence:** Give a short demo without relying on notes and answer why each major boundary exists.

**Exit:** V1 release checklist passes and known limitations are published.

## Suggested cadence

Use short milestones rather than a date-heavy schedule: plan → experiment → explain → implement → test → reflect. Keep commits small, and require a brief personal explanation before accepting AI-assisted code.

## Assumptions

- The owner is rebuilding fluency while completing a portfolio project.
- Narrow vertical slices will provide better learning and hiring evidence than feature breadth.
- Feasibility spikes may remove candidate features from V1.

## Non-goals

- A fixed deadline that rewards rushing past understanding.
- Parallel feature development before the first vertical slice is stable.
- Portfolio polish that hides weak tests or unexplained code.
- Treating generated code volume as progress.

## Open questions

- How many weekly hours can be protected consistently?
- Which concepts need separate study sessions before the feasibility spikes?
- What evidence best demonstrates independent ownership: journals, design notes, recorded demos, or all three?
- At what point should a suggestion type be cut rather than extended?

## Deferred decisions

- Calendar dates and release target.
- Hosting and public-demo strategy.
- Stretch goals after V1.
- Whether an AI-backed suggestion is necessary for the first public portfolio release.
