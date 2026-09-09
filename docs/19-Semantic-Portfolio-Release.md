# Semantic portfolio release

Branch: `feature/semantic-portfolio`. Started 2026-09-09.

## Plan and acceptance criteria

1. **Structure mapping:** connect direct-page marked-content IDs to structure elements and verify the parent-tree relationship. Show tag role, artifact, untagged, and unresolved states with linked page highlights. Report ambiguous, malformed, or unsupported form/object references explicitly.
2. **Figure alt text:** select a mapped existing Figure, inspect current text and page content, queue a reviewer-written replacement, and verify the saved Alt value.
3. **Explainable tagging proposals:** group direct-page text observations, suggest headings, paragraphs, list items and captions with reasons and heuristic confidence. Allow reviewer correction and acceptance. Write accepted groups with MCIDs, structure elements and parent-tree entries; do not interpret heuristic confidence as a calibrated probability.
4. **Reading order:** reorder existing sibling structure elements with keyboard-operable up/down controls linked to the page. Require an exact permutation and preserve every child reference once.
5. **Repair transactions:** combine alt-text, reading-order, accepted tagging, approved path artifacts and metadata changes in one export. Provide queue removal/undo, verification before download, before/after evidence and continuation from the output. The original stays immutable and failures return no partial output.
6. **Benchmark dashboard:** run a versioned synthetic regression corpus, display per-role precision/recall, coverage, error examples and a simple paragraph-only baseline. Support importing aligned comparison predictions, without inventing Acrobat measurements. Include repair preservation results.
7. **App accessibility:** keyboard workflows and focus management, status announcements, high-contrast/reduced-motion styling, automated browser accessibility checks and CI coverage.

## Verification strategy

Generate synthetic fixtures in memory or temporary storage. Test valid and broken structure references, named marked-content properties, role mapping, duplicate MCIDs, nested forms, multi-page references, alt text, sibling reorder, accepted tags and parent-tree updates. Verify saved document structure against the expected full object graph, text and page rasters against the original, and original byte hashes. Add transaction rollback/conflict and queue undo tests. Exercise real browser workflows and record benchmark results. Do not commit generated PDFs.

## Evidence and limitations

### Implemented surface

All seven planned slices are wired into the running workbench. Synthetic demos are generated in memory. The nested tag list links structure elements to page observations; the inspector filters tagged, untagged, artifact and unresolved content. A mapped Figure accepts reviewer-written `/Alt`. Existing children can be reordered without dropping references. Accepted layout-rule proposals write actual marked content and structure/parent-tree entries. The repair queue combines supported operations, captures source/version identity, supports removal and undo, and returns a PDF only after verification. It records approved operations, before/after values, hashes and page previews. Continuing from an export starts a fresh source-bound queue.

### Automated verification, 2026-09-09

- Java: **55 tests passed**, including 14 semantic service tests and a semantic/benchmark HTTP integration test. Cases cover named properties, role maps and explicit MCRs, duplicate MCIDs, malformed nested-form state isolation, broken parent trees, multi-page key allocation, accepted text groups, list structure, figure alt text, exact sibling permutations, combined metadata/artifact repairs, stale hashes, conflicts, null operations/roles, source immutability and refusal limits.
- JavaScript: **9 state tests passed**, including immutable export snapshots, source isolation, accepted-action limits, replacement/removal undo and sibling permutations.
- Browser: **6 workflows** cover semantic and combined repairs, reopening exported copies, all seven text proposals, path-plus-language repairs, failed export behavior, queue undo, downloads, keyboard movement/focus, mobile high contrast, reduced motion, membership filtering and benchmark errors. axe checks target WCAG 2 A/AA and 2.1 AA rules across idle, loaded, queued, exported and error states. These are automated checks, not a complete accessibility audit or screen-reader acceptance test.
- CI installs the locked Node dependencies and Chromium, builds the Java app, and runs the state and browser suites. Local browser execution requires permission to bind a temporary localhost port.

### Independent temporary round trip

`scripts/SemanticRoundTrip.java` creates two temporary experiments: (1) Figure alt text + reading order + one path artifact + language + display-title; (2) seven accepted text groups on an untagged source. For both, PDFBox reopen verified the expected full reachable catalog/Info graph, unchanged extracted text on all pages, identical bounded preview pixels, and immutable original bytes. Reinspection resolved every accepted group through its parent tree.

Poppler independently rendered each before/after at 144 DPI and extracted layout text. Matching PNG file hashes prove identical rendered output for these fixtures. The final renders were visually inspected: no clipping, missing text or altered chart was observed. All generated PDFs, PNGs and raw JSON records remain temporary under `/private/tmp/pdf-semantic-qa.RA3e0l`, outside Git.

| Experiment | Source PDF SHA-256 | Output PDF SHA-256 |
| --- | --- | --- |
| Combined repair | `711375cfc552b6c96a128c435a199c25575b321f6801a71e039c5d529c7709f6` | `63cf9334a3e9030bcfa4800e2f4c68769026842b9a2c1de8c52a23026a93f17b` |
| Accepted tags | `1d5621142e623f065a57205b398d960e4da437f0cd2fd446eb3e952adbe237a7` | `b474d79d22574251fce1181b3e6acbe5df83a3410b0c31fd9004cc6765b27d75` |

Matching before/after PNG SHA-256: combined `20424621b76d0ed3f03ccdca525deb73139b61e166e28d8caa46d98292e8f711`; new tags `316e1da2a906b907b8b3e311e89e34e9a724165bba9e301b0d7e9ea24c0900e4`.

Matching extracted-text SHA-256: combined `ce118f453a7629335139a29ebfc9d58e6fe5e18437949c772478f51ee783b664`; new tags `11425094b21c7a7efe39a5483847ea9cbea784f216b5421c4ca582801f3e0178`.

Reproduce with Java 21 after `mvn verify` (use any existing temporary output directory):

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/evidence-classpath.txt
java -Djava.awt.headless=true --class-path "target/classes:$(cat target/evidence-classpath.txt)" scripts/SemanticRoundTrip.java /path/to/temporary-directory
pdftoppm -r 144 -png -singlefile /path/to/temporary-directory/semantic-repaired.pdf /path/to/temporary-directory/semantic-repaired
pdftotext -layout /path/to/temporary-directory/semantic-repaired.pdf /path/to/temporary-directory/semantic-repaired.txt
```

Repeat Poppler for each source and output. The deterministic source hashes are stable within the pinned generator/dependencies; saved derivative hashes can vary with save-time metadata. macOS may need a Fontconfig configuration pointing at system fonts.

### Benchmark results

Corpus `synthetic-layout-v1`; algorithm `layout-rules-v1`. Two intentionally small one-page layouts, 14 labeled groups. Exact-text alignment is specific to these generated examples, not a general document segmentation metric. One proposal is aligned per expected group; unmatched groups count as abstentions. This benchmark measures role classification, not complete tree structure, reading order, tables, or real-world document quality.

| Predictor | Correct | Micro precision | Recall | Coverage |
| --- | --- | --- | --- | --- |
| Layout rules | 12/14 | 85.7% | 85.7% | 100% |
| Paragraph-only baseline | 5/14 | 35.7% | 35.7% | 100% |

The body-sized `IMPORTANT CONTEXT` heading is incorrectly proposed as P. `Figure 7 is discussed in the next paragraph.` is incorrectly proposed as Caption. These examples appear in the dashboard. H2 recall is 50%; Caption precision is 66.7%. There are zero abstentions and zero artifact predictions in this run—the rules do not currently propose artifacts. A separate comparison test proves that missing predictions count as abstentions and incorrect artifact predictions are counted.

Comparison JSON must match corpus version, exact source hashes and known row IDs/roles. Users can download both PDFs and the template and supply predictions measured with another tool. No Acrobat result or superiority claim is provided. The repair result shown beside metrics is one combined synthetic preservation smoke case, not a corpus-wide repair-success rate.

### Explicit limits and follow-ups

- This is human-reviewed, rule-based tagging, not an AI model or unattended replacement for Acrobat. Proposal scores are heuristic strength, not calibrated confidence. Adjacent layout grouping is basic; multi-column segmentation and semantic meaning need better evaluation.
- New tags support H1/H2/H3, P, LI and Caption. Each accepted list item currently becomes its own L → LI → LBody group, including the visible marker in LBody; grouping adjacent items and separating Lbl are later work. Accepted groups append in acceptance order. Reopen the output to edit that order.
- Reading order changes the `/K` sequence of existing siblings only. It does not reorder painted content, flatten nested structures, infer cross-column order, or establish an independently tested screen-reader sequence. Tagging and reordering the same parent in one transaction are rejected.
- Semantic review/transactions allow at most 20 pages, 25 actions, a bounded structure tree and bounded decoded data/content. Each page is rechecked. Existing unresolved structure blocks semantic writes. Nested form-stream MCRs, OBJR references, PDF 2 namespaces, annotation semantics, OCR and hidden/occluded content are not resolved. Inline-image pages are excluded from content rewriting.
- Geometry is an observation approximation, not pixel-perfect object selection. Artifacting still requires a human decorative-content decision. No blanket path/text artifacting, deletion of unmapped characters, or automatic artifacting of figures lacking alt text was added.
- Title writing and title/XMP modification remain disabled. Original uploads are unchanged. Download history and queues are local-session state, not durable storage. No external AI provider is contacted.
- Automated render/text/graph checks are preservation evidence, not proof of semantic accessibility. No PAC, veraPDF, PDF/UA, WCAG or legal compliance claim is made. Real-document corpus expansion and manual assistive-technology review remain necessary.
