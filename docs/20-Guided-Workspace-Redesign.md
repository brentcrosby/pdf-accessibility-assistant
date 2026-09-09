# Guided workspace redesign

Branch: `feature/guided-workspace`. September 9, 2026.

## Problem

The workbench grew into a long page containing the PDF, content inspection, tag editing, proposals, ordering, two queues, multiple export paths and a benchmark. Users had to scroll away from the document to edit it, then find their way back. All controls had similar emphasis, making the next action unclear.

## Reference workflows

Reviewed official product documentation, including its illustrated workflows. This was a documentation comparison, not a hands-on evaluation of licensed products.

- [Adobe Acrobat Reading Order](https://helpx.adobe.com/acrobat/using/touch-reading-order-tool-pdfs.html): connects highlighted page content with the active reading-order tool; offers the Tags panel for more detailed structure editing. Adopted the persistent page preview and selection-linked controls.
- [CommonLook Simplified Editor quick start](https://support.allyant.com/support/solutions/articles/156000367421-web-based-commonlook-pdf-simplified-editor-quick-start-guide): moves through assessment, image descriptions, page-element review and download; selecting warnings highlights their page locations. Adopted a visible task sequence, suggested next actions and a distinct export destination.
- [axesPDF](https://www.axes4.com/en/desktop-software/axespdf): combines document, logical structure, context-sensitive properties and review results in its workspace. Adopted adjacent document and tool views with details tied to the selected tag.

## Implemented flow

1. Open a PDF or an in-memory demo from a dedicated welcome screen.
2. Start in **Overview**, which links to mapped figures awaiting descriptions, pending text proposals, reading-order review and unresolved content. Document settings and detailed findings stay here.
3. Switch among **Page content**, **Tags & alt text**, **Suggested tags** and **Reading order** while the same PDF preview remains mounted. Only the active tool's controls are visible.
4. Inspect a tag to highlight its mapped page locations. Figure-description drafts and per-tool scroll positions survive navigation. Queued suggestions show an update state rather than presenting themselves as new work.
5. Use the persistent change count to reach **Review & export**. Queue changes, downloads, verification evidence, notes and reports share this destination. Older single-action export capabilities remain available, with metadata-only export under a disclosure.
6. Open **Benchmark** in its own dialog, then return to the current document and tool. Opening another PDF is a separate header action.

Desktop uses task navigation, a central page and a scrolling tool panel. At intermediate widths navigation becomes a horizontal strip. Small screens stack the viewer and active tool; choosing a task moves to its controls and locating content returns to the page. Navigation uses ordinary labeled buttons with an active state, optional arrow-key movement, and an explicit heading focus target. Benchmark uses a native dialog with Escape and focus restoration.

The semantic mapper, mutation rules and export verification are unchanged. Counts concern supported observations and pending queue entries; they do not represent a document compliance score. Unavailable semantic analysis offers page inspection and retry instead of leaving a loading message.

## Verification

Browser coverage exercises combined alt/order repairs, accepted text tags, path-plus-language exports, source isolation, unsuccessful verification, mobile forced colors, keyboard focus, benchmark dialog return, draft preservation, tag-to-page linking, and semantic retry. Screenshots of the desktop and mobile workspace are generated in ignored test output for visual inspection. The first accessibility pass identified low-contrast navigation numerals and footer text; both were darkened before the final check.

Final local result: **8 browser workflows and 9 state tests passed**. The browser suite includes axe accessibility checks across the relevant tool and dialog states. Desktop (1280 × 900) and mobile (390 × 844) renders were inspected. The packaged app builds successfully.

No PDF-writing backend behavior was changed and no derivative PDFs were added to Git. The redesign stays on its own branch for review.
