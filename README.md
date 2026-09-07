# PDF Accessibility Assistant

An AI-assisted PDF accessibility remediation tool that separates safe, deterministic metadata fixes from ambiguous suggestions requiring human review.

> [!IMPORTANT]
> This project is a remediation assistant, not a compliance guarantee. It does not guarantee PAC, PDF/UA, WCAG, Section 508, or legal compliance. Final accessibility evaluation requires appropriate automated and human testing.

## Status

**First implementation slice in progress.** The application now provides bounded upload and analysis, human metadata-review records, and a revalidated export path for selected deterministic metadata actions. Broader remediation remains deliberately out of scope.

## Planned V1 workflow

1. Upload a public or synthetic PDF.
2. Analyze title, document language, display-title preference, and basic tagged/untagged signals.
3. Record a human review decision for missing metadata values.
4. Export a new copy with only supported deterministic actions: enable display-title when a title already exists and apply a human-confirmed document language.
5. Revalidate the supported metadata checks and report remaining limitations.

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

The build targets Java 21. The initial implementation uses Spring Boot 3.5.16 and Apache PDFBox 3.0.8.

## Run locally

Prerequisites: Java 21 and Maven 3.9+.

```bash
mvn verify
mvn spring-boot:run
```

Open `http://localhost:8080` and upload a public or synthetic PDF no larger than 10 MB. The API is in-memory by design for this first slice; restarting the app removes uploaded files and review records.

### Current API

- `POST /api/documents` with multipart `file` and `sourceType` (`PUBLIC` or `SYNTHETIC`)
- `GET /api/documents/{id}`
- `POST /api/documents/{id}/reviews` with an issue code, `APPROVE` or `REJECT`, and an approved metadata value when required
- `POST /api/documents/{id}/export` to download a new PDF copy and supported revalidation headers

## Implemented boundary

- The original upload is retained unchanged in memory and every export begins from its original bytes.
- No document content is sent to an AI provider.
- Tagging signals are reported only; this application does not retag PDFs, repair reading order, modify headings, remediate tables/forms, or perform OCR.
- Missing title review can be recorded, but title writing is intentionally withheld in this first slice until the title/XMP synchronization spike demonstrates a safe round trip.
- An export result is not a PAC, PDF/UA, WCAG, Section 508, or legal-compliance result.

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
- [CT-001 Metadata Defect Profile](docs/10-CT-001-Metadata-Defect-Profile.md)
- [CT-001 PDFBox Inspection Spike](docs/11-CT-001-PDFBox-Inspection-Spike.md)

## Current evidence gate

Before any additional metadata action becomes automatic, it must pass its own controlled PDFBox write/save/reopen experiment and be added to the decision log. veraPDF and PAC remain later evidence sources; they are not integrated compliance guarantees.

## Portfolio goal

This project is intentionally learning-oriented. Progress is measured through narrow vertical slices, tests, written decisions, and the owner's ability to explain and independently change the work—not by generated code volume or feature count.

## License

This repository is licensed under the [MIT License](LICENSE).
