# First content repair: decorative paths

## Implementation plan

1. Retain the working language and display-title exports and make their entry point explicit.
2. Connect a selected path observation to its original page drawing operator. Check whether its complete drawing sequence can be isolated.
3. Offer one-path artifact export after the reviewer identifies the path as decorative. Support direct page paths outside existing marked content; explain unsupported selections, including form contents, clipping sequences and already marked content.
4. Insert `/Artifact BMC` and `EMC` around that exact drawing sequence in a separate copy. Reopen and verify the expected content tokens, the rest of the document object graph, extracted page text and rendered page pixels before returning the copy.
5. Provide a PDF download, repair evidence, before/after previews, and continuation from the repaired copy for subsequent repairs.
6. Exercise positive and refusal cases with synthetic PDFs, run the existing regressions, and verify the live app. Keep generated PDFs temporary and uncommitted.

This milestone implements a concrete content fix. Structure-tree mapping, text artifacting, figure alt-text changes and autotagging remain subsequent work. The reviewer supplies the decorative judgment; appearance preservation alone cannot establish that a path is decorative.

## Evidence

Implemented on `feature/review-workbench`, 2026-09-09. Existing uncommitted work was retained; nothing was committed or pushed.

### Automated checks

- `mvn verify`: 40 Java tests pass, including 17 artifact-service tests and one artifact HTTP integration test.
- `node --test src/test/js/*.test.mjs`: 5 review-state tests pass. Both browser modules pass syntax checks.
- Positive cases cover all four page rotations with a crop box, sequential repairs on a previous output, an unmarked path alongside a real structure/parent tree, a direct path following form content, shared page streams, and subsequent metadata export retaining an artifact.
- Refusal cases cover missing decorative approval, wrong source hash or geometry version, nonexistent/text regions, existing artifacts or MCIDs, clipping/interleaved paths, form contents, malformed instructions, and certified-document permissions.
- Injected changed rendering causes export refusal. A preservation-digest test detects a catalog change.
- An inline-image case initially failed the exact-token round-trip check. Such pages now receive an explicit refusal during preparation. Normal image XObjects remain supported. This is a recorded subset restriction, not a passed inline-image preservation claim.

### Independent renderer experiment

`ArtifactRepairExperiment` generates a four-page synthetic source, a repaired derivative and JSON/PNG evidence in an explicitly supplied temporary directory. The selected path was page 1's decorative separator (`p1-o4`). PDFBox confirmed the expected artifact wrapper, unchanged selected-page text/pixels, and the preserved catalog/Info graph.

- Source SHA-256: `27e010128c62d8215005672ca10941457be945caa74877c375ad282c68bec8d1`
- Repaired PDF SHA-256: `5d4f733b805cf6dc984139a954dc1c388bae7bab5d1e5f8a99dde6dca71e110e`
- Poppler rendered page 1 at 144 DPI. Before/after PNG files were byte-identical, SHA-256 `d26b25558539b511321ed227539b57db3b0af6b75ce15b895dcf9064089285ef`.
- Poppler extracted text for all four pages was byte-identical, SHA-256 `168253f76509ec4dcd5607573ae61d0cb4e66d7135a80b0cb585b0fe2c630e21`.
- The independently rendered page was visually inspected: text, separator, image and footer background remained intact.

The bundled Poppler font configuration initially pointed to unavailable cache directories. A temporary font configuration using macOS system fonts and a temporary cache resolved it. The final comparisons above completed successfully with that configuration.

### Browser workflow

The local app was exercised with the synthetic source. Checks confirmed that the repair button requires decorative confirmation, the server returns a real repaired PDF and evidence, the note is preserved, before/after previews load, and PDF/JSON download links have the intended filenames. The returned PDF bytes were re-uploaded using **Continue with this repaired copy**; checking the same path then correctly reported that it is already inside an artifact. Metadata export from this repaired source still applied the display-title setting. Browser console error/warning checks were empty.

The browser's file-save completion was not asserted. The underlying PDF bytes were tested through the real repair response and successful re-upload, and the links and JSON evidence were inspected. Repair history and downloads are session-local.

## Verification contract and limits

The request binds the selected region to its document ID, original SHA-256 and geometry version. Client-supplied drawing instructions are never accepted. The path is resolved again from the original, and only a contiguous construction/paint sequence can be wrapped. Replacing the selected page's Contents reference avoids modifying a stream shared with another page.

The saved copy must contain exactly the expected serialized content tokens. A canonical graph digest compares the reachable catalog and Info dictionary, excluding only the repaired page's Contents; dictionary order/object numbers and stream compression are ignored, while decoded stream data and logical references are compared. This includes tag and parent trees, other pages, metadata, resources and annotations reachable from the catalog. It does not promise byte-for-byte preservation of PDF serialization or unreachable/trailer-only objects. Signed/certified and encrypted inputs are excluded.

Selected-page PDFBox text and raster pixels must match before/after. The raster uses the workbench's bounded resolution (up to 1,800 pixels on the longest edge, at most 2x scale). Exact token/graph checks complement that visual check. These checks establish the stated preservation properties for a supported input, not the correctness of the reviewer's decorative judgment or accessibility conformance.

Repair limits add a 4 MB decoded page stream cap, 200,000 parsed tokens, 100,000 graph visits, depth 100 and 32 MB aggregate decoded stream data to the existing workbench limits. These remain application guardrails rather than a hardened process sandbox. The output must remain within the 10 MB intake limit to support continuation.

## Next milestones

1. Resolve marked-content IDs to the structure tree and show actual tagging membership on the page.
2. Add repairs for narrowly identified existing figures, including reviewer-written alternative text.
3. Extend artifact support to additional content forms only after targeted preservation experiments; keep bulk actions gated on reliable targeting.
4. Build an autotagging benchmark with human-reviewed references before judging quality against Acrobat.

Implementation references: [PDFBox stream engine](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/contentstream/PDFStreamEngine.java), [stream parser](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/PDFStreamParser.java), and [content writer](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/ContentStreamWriter.java).
