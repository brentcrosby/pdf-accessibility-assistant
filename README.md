# PDF Accessibility Assistant

An AI-assisted PDF accessibility remediation tool that separates safe, deterministic metadata fixes from ambiguous suggestions requiring human review.

> [!IMPORTANT]
> This project is a remediation assistant, not a compliance guarantee. It does not guarantee PAC, PDF/UA, WCAG, Section 508, or legal compliance. Final accessibility evaluation requires appropriate automated and human testing.

## Status

**Semantic review and verified repairs available locally.** Map tags to page content, write reviewer-approved figure alternative text, accept explainable text-tagging proposals, reorder existing siblings, and combine supported repairs into a verified export. A reproducible benchmark shows both successes and errors. See the [release plan and evidence](docs/19-Semantic-Portfolio-Release.md).

## Current workflow

1. Upload a public or synthetic PDF.
2. Inspect page content alongside mapped tags, artifacts and unresolved membership.
3. Review figure descriptions, suggested text roles, reading order, decorative paths and metadata.
4. Queue accepted repairs, remove or undo queue changes, and export a separate copy.
5. Verify the saved structure, all-page text and preview pixels; download the PDF and transaction record, or continue reviewing the output.

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
node --test src/test/js/*.test.mjs
mvn spring-boot:run
```

Open `http://localhost:8080` and upload a public or synthetic PDF no larger than 10 MB. The API is in-memory by design for this first slice; restarting the app removes uploaded files and review records.

Open the running server URL, not the HTML file directly. The review workbench requires its local API. JavaScript state tests use Node.js 22+. Browser and accessibility tests additionally use Playwright and axe:

```bash
npm ci
npx playwright install chromium
npm test
npm run test:browser
```

Build with `mvn verify` first; browser tests launch the built app on port 8085. CI runs both suites.

### Try semantic repairs

1. Choose **Load tagged demo**. Select a tag in the nested tag list or a region on the page to inspect its mapping.
2. Enter a meaningful description for the chart and choose **Queue alternative text**.
3. In **Reading order**, move the H1 before the paragraph using the up/down buttons, then **Queue reading order**.
4. Review the **Repair transaction** queue and choose **Verify and export queued repairs**. Download the separate PDF and transaction record. **Continue reviewing this export** reloads its saved tags and alt text in a fresh review session.
5. Choose **Load untagged demo** to review suggested headings, paragraphs, list items and captions. Correct roles if needed and accept individual proposals. Exporting writes actual structure elements, marked-content IDs and parent-tree links. New tags append in acceptance order; continue from the export to adjust sibling order.
6. Run the **Measured tagging benchmark**. Download its two PDFs and comparison template to evaluate another tool on the same labeled groups. Comparison scores are imported, not invented.

Semantic review is limited to 20 pages and a supported direct-page content subset. New tagging and reordering the same parent require separate exports. These are reviewed repairs, not unattended whole-document autotagging.

### Try the workbench

1. Analyze a public or synthetic PDF and navigate pages with the arrows or page number. Use zoom and the overlay toggle to inspect the original.
2. Filter by Text, Images, or Paths and search the current page. Either select a region on the preview or use the keyboard-accessible region list.
3. Use **Select visible** to select the currently filtered page regions. Record **Keep as content**, **Artifact candidate**, or **Defer**. Selection clears when changing filters or pages so bulk actions do not include hidden regions.
4. Add notes, locate a queue item on its page, remove a decision, or undo up to 50 recent changes. A batch decision is undone as one change.
5. **Prepare review report**, then use its **Download review report (JSON)** link. The report includes source name/hash, inspected pages, normalized geometry, notes, timestamps, and proposed decisions. An on-screen JSON preview lets you inspect the prepared snapshot. Prepare again after further edits.
6. The older metadata-only export remains available. You can also queue language and display of an existing title in a combined repair transaction. Export records include a snapshot of the review plan captured when export was requested.

Review plans last until reload or a new upload. Metadata reviews and original uploads are held by the running server until restart. There is no external AI call or automatic bulk artifact/deletion operation.

### Apply an actual content repair

1. Select a single path in the page or region list and choose **Prepare artifact repair**.
2. If the drawing is supported, confirm it is decorative and conveys no information. Add a repair note if helpful.
3. Choose **Apply artifact and export PDF**. The server writes a separate copy and verifies the reopened content, remaining document structure, page text and preview pixels.
4. Download the repaired PDF and JSON repair record from **Repair history**. Expand **Compare before and after** to inspect both page previews.
5. Use **Continue with this repaired copy** to start a fresh review session and apply another repair. Prior decisions are included in the saved repair record; they are not automatically replayed. Metadata fixes can then be exported from the repaired copy.

The first repair supports contiguous direct-page paths outside existing marked content. Existing artifacts, tagged/marked paths, paths inside reusable forms, interleaved/clipping paths, pages with inline images, signed/certified documents and inputs beyond verification limits are declined. Ordinary separate PDF image objects are supported. See the [plan, verification evidence and limits](docs/18-Artifact-Repair-Plan-and-Evidence.md).

### Current API

- `POST /api/documents` with multipart `file` and `sourceType` (`PUBLIC` or `SYNTHETIC`)
- `GET /api/documents/{id}`
- `GET /api/documents/{id}/original` for the original PDF
- `GET /api/documents/{id}/pages/{pageNumber}/observations` for page content regions and coverage warnings
- `GET /api/documents/{id}/pages/{pageNumber}/preview` for a PNG of the original page
- `GET /api/documents/{id}/text-regions` (legacy; use page observations for the workbench)
- `POST /api/documents/{id}/reviews` with an issue code, `APPROVE` or `REJECT`, and an approved metadata value when required
- `POST /api/documents/{id}/export` to download a new PDF copy and supported revalidation headers
- `POST /api/documents/{id}/artifact-repairs/check` to check a selected path
- `POST /api/documents/{id}/artifact-repairs` to export a verified artifact copy; JSON contains base64 PDF/preview bytes and structured evidence
- `POST /api/demos/semantic` or `/api/demos/untagged` to load an in-memory synthetic demo
- `GET /api/documents/{id}/semantics` for structure mapping and explainable proposals
- `POST /api/documents/{id}/repair-transactions` with source hash, semantic version and accepted operations; returns base64 PDF/previews and verification evidence
- `POST /api/benchmarks/run` or `/api/benchmarks/compare` for measured regression results
- `GET /api/benchmarks/samples/1` or `/api/benchmarks/samples/2` for reproducible synthetic comparison PDFs

## Implemented boundary

- The original upload is retained unchanged in memory and every export begins from its original bytes.
- The workbench previews original pages with crop/rotation-aware text, image, and path bounds. Observations are not confirmed accessibility flags. Text runs are not necessarily paragraphs; images are not necessarily semantic figures; paths are not necessarily decorative.
- Review-plan decisions (`KEEP_AS_CONTENT`, `ARTIFACT_CANDIDATE`, `DEFER`) remain evidence only. The separate repair queue explicitly accepts actions that alter the exported copy. Unicode mapping failures remain diagnostic, not deletion recommendations.
- Artifact repair writes `/Artifact BMC` and `EMC` around each individually approved supported drawing sequence. Transactions support up to 25 accepted actions; there is no blanket “artifact everything” operation.
- Semantic mapping checks page MCIDs against both structure ownership and parent-tree entries, including supported role maps and named properties. Ambiguous or unsupported references remain unresolved. Figure alt text and sibling ordering are writable only within the supported structure subset.
- Preview coverage excludes annotation regions, optional-layer visibility, occlusion, masks and pattern details. Bounds approximate painted content. Only loaded pages are inspected. Page-level limits and truncation are reported explicitly; see the [workbench evidence and limits](docs/17-Review-Workbench-Goal-and-Evidence.md).
- Export history is limited to the current browser session. It records the original filename and SHA-256, applied actions, revalidation summary, export timestamp, and download links for each exported copy and its JSON remediation record; it is cleared on page reload and is not persisted.
- No document content is sent to an AI provider.
- Accepted text proposals can create H1/H2/H3, P, LI/LBody and Caption structures. Existing siblings can be reordered. The application does not provide arbitrary tag-tree surgery, table/form remediation, OCR, text deletion, or full-document accessibility certification.
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
- [CT-001 Title-Metadata Transformation Plan](docs/12-CT-001-Title-Metadata-Transformation-Plan.md)
- [CT-001 Title/XMP Round-Trip Experiment](docs/13-CT-001-Title-XMP-Round-Trip-Experiment.md)
- [CT-002 Document-Language Round-Trip Experiment](docs/14-CT-002-Document-Language-Round-Trip-Experiment.md)
- [DT-001 Display-Document-Title Round-Trip Experiment](docs/15-DT-001-Display-Document-Title-Round-Trip-Experiment.md)
- [Encrypted and Malformed PDF Intake Experiment](docs/16-Safety-Encrypted-and-Malformed-Intake-Experiment.md)
- [Review Workbench Goal and Evidence](docs/17-Review-Workbench-Goal-and-Evidence.md)
- [Artifact Repair Plan and Evidence](docs/18-Artifact-Repair-Plan-and-Evidence.md)
- [Semantic Portfolio Release: Plan and Evidence](docs/19-Semantic-Portfolio-Release.md)

## Current evidence gate

Before any additional metadata action becomes automatic, it must pass its own controlled PDFBox write/save/reopen experiment and be added to the decision log. veraPDF and PAC remain later evidence sources; they are not integrated compliance guarantees.

## Portfolio goal

This project is intentionally learning-oriented. Progress is measured through narrow vertical slices, tests, written decisions, and the owner's ability to explain and independently change the work—not by generated code volume or feature count.

## License

This repository is licensed under the [MIT License](LICENSE).
