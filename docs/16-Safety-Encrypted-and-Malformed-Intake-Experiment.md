# Encrypted and Malformed PDF Intake Experiment

## Purpose and boundary

This synthetic-fixture safety experiment establishes the current V1 intake behavior for password-protected and malformed PDF inputs. It is not an attempt to decrypt, repair, export, or remediate either input type, and it is not a compliance claim.

## Results

| Synthetic input | Processing boundary | User-facing result | Mutation/export result |
| --- | --- | --- | --- |
| Password-protected one-page PDF | PDFBox raises `InvalidPasswordException` while analysis opens the input. | `Password-protected PDFs are not supported in this V1 slice.` | No document is stored, no export is possible, and no derivative is created. |
| Truncated bytes beginning with `%PDF-1.7` | PDFBox cannot open the malformed bytes. | `The file could not be opened as a supported, unencrypted PDF.` | No document is stored, no export is possible, and no derivative is created. |

The new encrypted-input branch catches only PDFBox's `InvalidPasswordException`; all other parsing failures retain the generic bounded error. Neither message includes input text, passwords, paths, parser details, or document metadata.

## Verification

- The focused `PdfAnalysisServiceTest` suite passed three checks: normal bounded analysis, encrypted rejection, and malformed rejection.
- The encrypted fixture is generated in memory with a synthetic PDFBox `StandardProtectionPolicy`; the malformed fixture is a small synthetic byte array. Neither was written to a repository or output directory.
- Intake analyzes bytes before constructing a `StoredDocument`, so a rejected input cannot be retained for review or exported. Existing export behavior still begins from immutable original bytes only after successful intake.

## Limitations

This V1 behavior deliberately does not accept passwords, inspect encrypted content, attempt decryption, repair malformed PDFs, or distinguish all possible parser failure classes. It does not make a statement about a document's accessibility.
