# Review workbench: goal and evidence

Date: 2026-09-09. Branch: `feature/review-workbench`.

This records the review-only milestone. The subsequent [artifact repair milestone](18-Artifact-Repair-Plan-and-Evidence.md) adds the first explicit content-writing action.

## Goal

Build a substantial local review workbench that makes proposed repairs concrete before PDF structural writing. Provide page-aligned text, image, and path observations, page navigation and zoom, filters and search, individual/batch review decisions with notes and undo, a visible queue, and standalone downloadable review evidence. Preserve original bytes and the existing metadata export allowlist. Verify geometry against rendered pixels and exercise the complete review workflow with synthetic data.

## Delivered behavior

- One page at a time is inspected and rendered. Geometry uses normalized top-left coordinates on the displayed crop box, including 0/90/180/270-degree page rotation. Text bounds come from glyph outlines when available, with approximate bounds otherwise.
- Text, image and painted path observations have distinct overlay colors and stable IDs within a source and geometry version. Filters and search affect the visible page list and overlays together.
- A keyboard-accessible list provides an alternative to clicking small or overlapping outlines. Larger background paths are drawn below smaller overlay buttons.
- Individual or bulk selections can receive Keep as content, Artifact candidate or Defer. Selection clears on filter/page changes. Notes default an unreviewed region to Defer. The queue provides locate/remove operations and undo (up to 50 changes; each batch is one change).
- The standalone JSON review plan contains source identity, inspected-page coverage, each decision's source region and bounds, notes, timestamps, geometry version and `appliedToPdf: false`. A visible preview and download link refer to the same prepared snapshot. A later metadata export captures its own independent review-plan snapshot.
- Async page requests are cancelled and stale responses ignored. Preview image URLs are revoked when replaced. Retry, blank-page and no-matching-content states are explicit. Local HTML-file mode points users to the running server.

## Validation evidence

Automated verification: `mvn verify` passes 22 Java tests; `node --test src/test/js/*.test.mjs` passes 5 JavaScript tests. CI runs both suites. Rendering tests run headless so they do not depend on a desktop graphics session.

Meaningful new cases include:

- Known cropped rectangle bounds compared against actual dark pixels rendered at all four page rotations.
- Text above its baseline, normalized bounds inside the crop box, stable repeated inspection, and unchanged source bytes.
- Transformed Form XObjects with an outer path preserved across form processing.
- Blank and image-only pages, invalid page numbers, unknown documents, no-store response headers, large-page image scaling and explicit partial-region limits.
- Bulk decision deduplication and atomic undo; invalid IDs/decisions cannot partially update state; filter combinations; notes and immutable report snapshots; independent documents and page navigation.

Browser checks used a temporary four-page synthetic fixture: mixed text/paths/image, cropped page rotated 90 degrees, blank page, and image-only page. The fixture is generated from `WorkbenchFixture` test source and is not committed as a PDF. Browser checks confirmed seven observations on the mixed page, two-path bulk review and undo, text search and notes, navigation with queue retention, overlay visibility toggle, zoom, empty-page behavior, keyboard region selection, and queue-to-page navigation. Report JSON was inspected in the app and included the exact selected regions, their bounds and source identity.

The in-app browser's download-event waiter did not report a filesystem download event for blob URLs. The workbench therefore provides an ordinary visible download link plus an inspectable JSON snapshot; report contents and link attributes were verified. Filesystem save completion was not asserted from that automation event.

Final handoff: repackaged and started the application on `127.0.0.1:8080`, reloaded the browser, and uploaded the synthetic fixture again. Verified the finished build's rendered preview, retained keyboard focus after region selection, and Reset filters behavior. The sample remains available in the live app's memory; the temporary fixture file was removed. Existing uncommitted work was preserved on the feature branch, and no changes or PDF derivatives were committed.

## Scope and limits

Regions are observations, not semantic tagging verdicts or accessibility error locations. Existing document-level metadata/tagging findings remain separate. In particular:

- No region is declared untagged merely because it is text. Structure-tree membership is not resolved.
- Image observations do not establish a semantic Figure or whether it lacks alternative text.
- Paths are not automatically classified as decorative. No content deletion or artifact/tag writing occurs.
- Missing Unicode mappings are reported as a diagnostic count; neither successful mappings nor missing mappings prove correct encoding or justify deletion.
- Bounds can overestimate visible content. Occlusion, optional layers, soft masks, text clipping, annotation appearances, shading/pattern internals and semantic grouping require further work. Fallback fonts may affect geometry and previews.

Rendering is capped at a 1,800-pixel longest edge and 2x scale. Page dimensions must be between 1 and 20,000 PDF units on each axis. Individual images above 25 megapixels are rejected. Extraction is limited to 1,500 regions, 100,000 operator/glyph steps, five seconds of observed processing and a nesting check. Partial extraction is labeled and complex previews are declined. These are application guardrails, not a hardened PDF processing sandbox or absolute CPU/memory guarantee. The legacy whole-document text route is limited to 100 pages.

The report schema is version 2.0. Export evidence now contains a nested `reviewPlan`; consumers of the earlier `textRegionReviewerDecisions` field should migrate. No claims about PAC, PDF/UA, WCAG, Section 508, legal compliance, or superiority to Adobe were made.

## Next evidence gates

1. Resolve marked-content IDs and structure-tree membership to distinguish untagged content from already-tagged content.
2. Locate semantic figures and their existing alternative text before offering a missing-alt-text workflow.
3. Add a controlled artifact-writing round trip for an explicitly approved decorative path, verifying pixels, text extraction, structure references and unchanged originals.
4. Expand the public/synthetic benchmark before enabling bulk artifact writing or autotagging.

The geometry implementation uses PDFBox's graphics-stream hooks. Reference: [Apache PDFBox 3.0.8 PageDrawer source](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/rendering/PageDrawer.java).
