# DT-001 Display-Document-Title Round-Trip Experiment

This temporary synthetic-PDF experiment tested PDFBox 3.0.8 on 2026-09-08. It is evidence only, not an implementation change, allowlist decision, or compliance claim.

| Case | Reopened title | Reopened `DisplayDocTitle` | PDF SHA-256 |
| --- | --- | --- | --- |
| Titled baseline | `DT-001 titled baseline` | false | `ce61c6900b4cec22fddc9c4980ba6f683d00f5d6fc61b475a4a51d7481b63bed` |
| Titled, application-path preference enabled | unchanged | true | `4f857ded5d2bcc79176ef0b28fe04c59c5520d905ddf00c402fecec4b36b80e3` |
| Untitled baseline | absent | false | `ed90f8f325acb7462c0e649c8e9f2b61efc60dc04954294d1a300377e8554d08` |
| Untitled, application-path boundary | absent | false | `84793922cd753f7715bbfe28dca8d7c16b05a0813d566bbdd599fdf610633ed6` |

Each case was one page, unencrypted, and written from separately retained original bytes. The runner asserted that both original byte arrays were unchanged and that the title, page count, encryption state, and expected preference survived save/reopen. The positive path set the preference only when the information-dictionary title existed; the negative path left it disabled when no title existed.

All four page-1 renders at 150 dpi had SHA-256 `9eb1d2ae9d50432d4ff21f57fb6f8ef6089ad92c7d5ae4a3254263a6abc94e40`; visual inspection found no visible-content change.

The experiment is limited to PDFBox persistence for this synthetic fixture. It does not establish that a title is meaningful, enable title writing, expand the deterministic allowlist, or establish PAC, PDF/UA, WCAG, Section 508, or legal compliance. Temporary PDFs, renders, runner, and extracted runtime files were deleted after recording this evidence.
