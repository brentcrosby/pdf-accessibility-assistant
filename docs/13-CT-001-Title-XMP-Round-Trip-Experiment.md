# CT-001 Title/XMP Round-Trip Experiment

## Purpose and boundary

This temporary feasibility experiment executed the two-stage procedure in [the title-metadata transformation plan](12-CT-001-Title-Metadata-Transformation-Plan.md). It tested whether PDFBox can remove and restore a known title consistently in CT-001's document-information dictionary and XMP `dc:title` `x-default` entry while preserving the stated technical invariants.

It is not a production implementation, an automatic-action decision, a PAC result, a PDF/UA or WCAG result, or a legal-compliance claim. No public-source PDF bytes or derivatives were added to Git.

## Environment and input

| Item | Value |
| --- | --- |
| Run date | 2026-09-08 |
| Source | [Frequently Asked Questions about Caltrans ADA Infrastructure Program](https://dot.ca.gov/-/media/dot-media/programs/local-assistance/documents/ada/2022/faqs-caltrans-ada-infrastructure-program.pdf) |
| Source SHA-256 | `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76` (matched the manifest before the run) |
| Java | Oracle Java `21.0.9` |
| PDF library | PDFBox `3.0.8`; XMPBox `3.0.8` |
| Neutral restored value | `CT-001 title round-trip test` |

The source bytes were copied before mutation. Stage A and Stage B were separately named temporary outputs; neither overwrote an input.

## API path tested

- PDFBox `Loader.loadPDF`, `PDDocumentInformation.setTitle`, and `PDDocument.save`.
- The existing XMP stream was parsed with XMPBox `DomXmpParser` in non-strict mode. Strict parsing rejected the source's existing `xmpMM:subject` property because it has no recognized type, so non-strict parsing was required to preserve and process this real-world XMP packet.
- For Stage A, the existing Dublin Core title property was removed with `DublinCoreSchema.removeProperty`.
- For Stage B, `DublinCoreSchema.setTitle("x-default", "CT-001 title round-trip test")` set the required language alternative. XMPBox's two-string overload takes language first, then title value.
- XMPBox `XmpSerializer` serialized the edited packet back into the original `PDMetadata` stream.

## Save/reopen results

| Check | Baseline | Stage A: missing title | Stage B: restored title |
| --- | --- | --- | --- |
| PDF SHA-256 | `9cb5afa71c87950b2f5a92985a1c1a6be4c8f922623c683dfede7c812d0f8f76` | `d77535fae8ae25e670f4aed10e2750456ba1d61ee6ea16e5de46a885d0aac03b` | `6cf4da550f99f54bffd55304f2944d9c3f0dcffaeb66a54925c518539b889e74` |
| Information-dictionary title | Original Caltrans title | Absent | `CT-001 title round-trip test` |
| XMP `dc:title` `x-default` | Original Caltrans title | Absent (the property was removed) | `CT-001 title round-trip test` |
| Page count | 6 | 6 | 6 |
| Encrypted | false | false | false |
| Catalog language | `en` | `en` | `en` |
| `DisplayDocTitle` | true | true | true |
| Mark information / `Marked` | present / true | present / true | present / true |
| Structure-tree root | present | present | present |

The runner also compared every non-title document-information entry after reopening; all matched the baseline, including author, company, creation and modification dates, creator, keywords, producer, source-modified value, and subject. The parsed XMP packet retained the same 19 non-title properties by namespace, property name, and XMP type in both stages.

## XMP normalization observation

XMPBox reserialized the XMP packet, so its raw SHA-256 changed even where non-title properties were retained:

| XMP packet | SHA-256 |
| --- | --- |
| Baseline | `90d9a8937c2b3314618a130f9f0c0c2fa651ec4f0278641b5ea4e581cf546935` |
| Stage A | `2d628f2327663ca570a1ee3b0cc96ea76889e1dc239ed9c627fb899c3334ce59` |
| Stage B | `f374960af392da9e53fd4a3a6aa069bb1e9d188813bbebd2dece234a3669d5c9` |

This experiment establishes semantic title synchronization for this fixture, not byte-for-byte XMP preservation. Any later feature work should preserve this save/reopen validation boundary and consider broader fixture coverage before changing the allowlist.

## Visual check

Page 1 of the baseline, Stage A, and Stage B was rendered at 150 dpi with Poppler. All three PNGs had the same SHA-256, `f3422b78a6f555e346f31a623a89bb0171bd1710fc557a3c57aa11e5d5560e28`, and visual inspection found no visible-content change.

## Result

The two required temporary stages passed for CT-001 under the environment above. This is limited evidence for a future review-required decision; it does not add title writing to the application or its automatic-action allowlist. The temporary PDFs, renderings, runner, and downloaded source were deleted after this evidence was recorded.
