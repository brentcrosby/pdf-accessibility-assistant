# PDF Accessibility Assistant

An AI-assisted PDF accessibility remediation tool that separates safe, deterministic metadata fixes from ambiguous suggestions requiring human review.

> [!IMPORTANT]
> This project is a remediation assistant, not a compliance guarantee. It does not guarantee PAC, PDF/UA, WCAG, Section 508, or legal compliance. Final accessibility evaluation requires appropriate automated and human testing.

## Status

**Planning and feasibility stage.** The application has not been scaffolded yet. The current work defines V1, validates technical assumptions, and protects the boundary between automatic changes and human-reviewed suggestions.

## Planned V1 workflow

1. Upload a public or synthetic PDF.
2. Analyze a limited set of basic accessibility issues.
3. Apply only allowlisted deterministic metadata fixes.
4. Present ambiguous remediation suggestions for approval or rejection.
5. Export a new remediated PDF without modifying the original.
6. Revalidate supported checks and report remaining limitations.

## Safety and data boundary

Development and demonstration use only public or synthetic PDFs unless workplace use has been explicitly approved. Document content must not be sent to an external AI service until the provider, disclosure, consent, and data-handling approach have been deliberately accepted.

## Intended stack

- Java 21
- Spring Boot and Maven
- Spring Web and Bean Validation
- Apache PDFBox
- veraPDF where practical
- JUnit 5 and Mockito
- GitHub Actions

The stack is an intended direction, not evidence that implementation already exists.

## Project headquarters

- [Project Brief](docs/01-Project-Brief.md)
- [V1 Scope](docs/02-V1-Scope.md)
- [Architecture Notes](docs/03-Architecture-Notes.md)
- [Learning-Oriented Roadmap](docs/04-Roadmap.md)
- [Decision Log](docs/05-Decision-Log.md)
- [V1 Issue and Remediation Matrix](docs/06-V1-Issue-Remediation-Matrix.md)
- [Learning Log](docs/07-Learning-Log.md)
- [Initial Backlog](docs/08-Initial-Backlog.md)
- [Test Corpus Manifest](docs/09-Test-Corpus-Manifest.md)

## Current planning gate

No production application scaffolding begins until the initial PDFBox and veraPDF feasibility spikes provide enough evidence to confirm the deterministic-fix allowlist and select the first human-review suggestion.

## Portfolio goal

This project is intentionally learning-oriented. Progress is measured through narrow vertical slices, tests, written decisions, and the owner's ability to explain and independently change the work—not by generated code volume or feature count.

## License

This repository is licensed under the [MIT License](LICENSE).
