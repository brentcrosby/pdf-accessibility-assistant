# CT-002 Document-Language Round-Trip Experiment

## Purpose and boundary

This temporary feasibility experiment tested the PDF catalog's document-language value using a synthetic, one-page bilingual sample. It establishes only how PDFBox 3.0.8 writes and reopens selected catalog strings, and how the application's existing Java BCP 47 guard distinguishes the tested valid and invalid inputs.

It is not a production implementation, an allowlist decision, a PAC result, a PDF/UA or WCAG result, or a legal-compliance claim. No PDF created for this experiment was added to Git.

## Environment and temporary input

| Item | Value |
| --- | --- |
| Run date | 2026-09-08 |
| Input | Synthetic one-page bilingual sample: `English and francais` |
| Baseline SHA-256 | `6673058186a1fb8282151d1e71d3e7a26ed3aa8eb324cc8c6b2276374b4028a1` |
| Java | Oracle Java `21.0.9` |
| PDF library | PDFBox `3.0.8` |
| Initial catalog language | Absent |
| Non-target baseline properties | Title `CT-002 synthetic language round-trip baseline`; `DisplayDocTitle=true`; one page; unencrypted; no marked flag or structure-tree root |

The baseline bytes were held in memory and separately written only as a temporary comparison file. Each case loaded the original baseline bytes, set only the catalog language with `PDDocumentCatalog.setLanguage`, saved a separately named temporary output, and reopened that output with `Loader.loadPDF`.

## Save/reopen results

| Case | Application-style BCP 47 guard | Reopened catalog language | PDF SHA-256 |
| --- | --- | --- | --- |
| Valid primary language: `en` | Accepted | `en` | `91bc288bada42a463c4cec6a0505f02edc3d868f65d8b75f051d74542673f6a3` |
| Valid language plus region: `en-US` | Accepted | `en-US` | `6d2166b6f505696327a246e661aa76a0b89fc42ac28166438edd2974b5197994` |
| Invalid boundary: `en_US` | Rejected | `en_US` when written directly through PDFBox | `d60a919380cee1aefc1a14d780cd2a1704478b348c75dc4b434ab00f1408007b` |
| Multilingual/ambiguous boundary: `en-US,fr-FR` | Rejected | `en-US,fr-FR` when written directly through PDFBox | `77c05de2cabe5d97be4f69dc440dc8ee7bb5f58fa0c6b4f40267bc54f664c0c3` |

The guard mirrors the current application rule: `Locale.forLanguageTag(value).toLanguageTag()` must not be `und` and must equal the supplied value ignoring case. It accepted `en` and `en-US`; it rejected the underscore form and the comma-separated value.

PDFBox itself did not validate either rejected string. When bypassing the application guard and writing directly with `setLanguage`, PDFBox saved and reopened both strings verbatim. Therefore, successful PDFBox save/reopen is evidence of string persistence, not evidence that a language tag is valid or suitable.

For all four saved outputs, reopening confirmed the expected language value while retaining the baseline page count, unencrypted state, information-dictionary title, `DisplayDocTitle=true`, absent mark-information flag, and absent structure-tree root. The runner also asserted that the original baseline bytes still matched the file written before each derivative was created.

## Visual check

Page 1 of the baseline and all four temporary outputs was rendered at 150 dpi with Poppler. Every PNG had SHA-256 `780a254ceefed2a7e1ff8f37c44e8b6db444ddac0839b89652fd144d20009170`; visual inspection found no visible-content change.

## Limitations and result

A single document-level `/Lang` value cannot express the language of every span in a multilingual PDF. The synthetic sample's English and French content therefore demonstrates a limitation, not a multilingual-remediation solution. Selecting a document language remains a human-confirmed choice, and language changes must not infer a value from content.

This experiment supports retaining the existing BCP 47 input guard and save/reopen verification for the two tested valid forms. It does not expand the deterministic allowlist, establish broad BCP 47 coverage, prove semantic correctness for multilingual documents, or justify any automated language selection.

The temporary PDFs, PNG renders, runner source/class files, and Maven classpath file were deleted after this evidence was recorded.
