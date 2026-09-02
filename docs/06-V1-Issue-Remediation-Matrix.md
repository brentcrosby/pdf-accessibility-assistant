# PDF Accessibility Assistant — V1 Issue and Remediation Matrix

## How to use this matrix

This is a planning gate, not a statement of implemented support. A candidate may enter the V1 allowlist only after its experiment succeeds on the approved public/synthetic corpus and its failure behavior is documented.

### Classifications

- **Automatic candidate:** potentially deterministic, but not automatic until evidence promotes it to the allowlist.
- **Review-required candidate:** requires human-supplied context or approval before mutation.
- **Report-only:** detected and explained, but not remediated in V1.
- **Unsupported/deferred:** outside the safe V1 boundary.

## Initial matrix

| Candidate issue | Proposed detection evidence | Initial classification | Proposed action | Verification | Main risks | Required planning/spike evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Missing document title metadata | PDF information dictionary and XMP metadata inspection | Review-required candidate | Ask the user for a title, then synchronize approved title metadata where safe | Reopen with PDFBox; compare fields; run supported validation | Inventing a title; inconsistent information-dictionary/XMP values; damaging metadata | Map relevant fields and round-trip representative PDFs |
| Viewer not configured to display document title | Catalog viewer-preferences inspection | Automatic candidate | Enable display of the document title when a valid title exists | Reopen and assert the catalog value; inspect in a PDF viewer | Applying the preference when no meaningful title exists; library-version differences | Confirm exact PDFBox behavior and preservation across fixtures |
| Missing or invalid document language | Catalog language inspection and language-value validation | Review-required candidate | Ask the user to select or confirm the document language, then write the approved value | Reopen; verify normalized value; run supported validation | Incorrect inference; multilingual documents; invalid language tags | Define accepted value format and test multilingual/report-only behavior |
| Encrypted or permission-restricted PDF | PDFBox load/encryption state | Report-only or unsupported | Explain why processing is blocked or limited | Confirm no output mutation and no content leakage | Password handling; misleading partial results; rights restrictions | Test representative synthetic encrypted/restricted files and define stop rules |
| Invalid or malformed PDF | Parser failure and controlled validation errors | Report-only or unsupported | Reject safely with a useful error | Confirm original remains unchanged and logs omit content | Parser vulnerabilities; crashes; vague errors | Build synthetic malformed fixtures and catalog safe failure behavior |
| PDF is untagged | Mark information and structure-tree-root inspection | Report-only | Explain that an untagged PDF needs substantial structural remediation outside V1 | Cross-check with available validator and manual inspection | Equating “tagged” with “accessible”; false conclusions from one flag | Establish a defensible tagged/untagged check across fixtures |
| Figure lacks alternative text | Existing structure elements, figure tags, and associated text properties | Review-required candidate | Suggest alt text only for a safely targetable existing figure element; require approval | Reopen structure; compare exact target; manual review; supported validation | Wrong target; hallucinated description; decorative images; sensitive content | Prove stable figure targeting and safe mutation before choosing for V1 |
| Suspicious heading level | Existing structure tree and neighboring heading sequence | Review-required candidate | Suggest a level change with context and rationale; require approval | Reopen structure; confirm target and approved level; manual review | Visual style mistaken for semantics; skipped levels may be intentional; unsafe tag-tree edits | Determine whether reliable context and safe structure mutation are feasible |
| Missing bookmarks in a long document | Page count plus outline inspection | Report-only initially | Explain the finding and possible manual follow-up | Manual inspection | Page-count threshold is contextual; generated bookmarks may misrepresent structure | Decide whether this adds value without expanding V1 mutation scope |
| Reading-order problems | Structure/content order comparison and human/assistive-technology review | Unsupported/deferred | Explain limitation and recommend expert/manual review | Manual screen-reader and PAC review where appropriate | Highly contextual; difficult mapping; destructive changes | None for V1; reconsider only after the narrow workflow is stable |
| Table structure problems | Structure-tree inspection plus human context | Unsupported/deferred | Explain limitation and recommend expert/manual review | Manual expert review | Headers, spans, scope, and complex relationships require substantial judgment | Deferred beyond V1 |
| Form-field accessibility | AcroForm inspection plus human context | Unsupported/deferred | Explain limitation | Manual keyboard and screen-reader review | Labels, instructions, error handling, and widget relationships are complex | Deferred beyond V1 |
| Scanned/image-only PDF requiring OCR | Text/image heuristics | Unsupported/deferred | Report likely OCR need without claiming certainty | Manual inspection | False classification; OCR quality and language issues | Deferred beyond V1 |

## Provisional V1 target

Subject to successful spikes, aim for:

- **Deterministic allowlist:** display-title preference and technically safe synchronization of user-approved metadata values.
- **Review-required inputs:** document title and language supplied or confirmed by the user.
- **One ambiguous demonstration:** alt text or heading level, but only if stable targeting and safe mutation are proven.
- **Report-only foundation:** encrypted/restricted, malformed, tagged/untagged, and selected unsupported-condition explanations.

## Promotion checklist

Before any action becomes automatic:

- [ ] Inputs and preconditions are deterministic and documented.
- [ ] The mutation is restricted to one typed, allowlisted action.
- [ ] The original remains unchanged.
- [ ] Save/reopen verification succeeds across the reference corpus.
- [ ] Unrelated PDF properties remain preserved within documented expectations.
- [ ] Failure produces no partial or silent mutation.
- [ ] Tests cover positive, negative, and boundary cases.
- [ ] The decision log records the supporting evidence.
- [ ] The UI and change summary describe the action accurately.
- [ ] No AI confidence score is used as a substitute for these checks.

## Assumptions

- Detection details will change as PDFBox and veraPDF behavior is learned.
- User-confirmed values can still require technical validation before writing.
- A safe metadata change may be deterministic even when choosing the correct human-language value is not.

## Open questions

- Should title changes update both the information dictionary and XMP in V1?
- Which language-tag standard and validation library should define accepted values?
- Can one structure-level suggestion be implemented without overstating V1 capability?
- Which checks can veraPDF support directly, and which remain application-specific observations?

## Non-goals

- Treating this initial matrix as final technical truth.
- Inferring semantic values automatically because a model is confident.
- Expanding V1 to full tag-tree, table, form, reading-order, or OCR remediation.

## Deferred decisions

- The first ambiguous suggestion type.
- Exact validator profiles and result mappings.
- Exact supported-PDF limits and corpus size.
