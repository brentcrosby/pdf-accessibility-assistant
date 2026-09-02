# PDF Accessibility Assistant — V1 Scope

## V1 workflow

1. Upload an allowed PDF.
2. Validate the file type and configured size/page limits.
3. Analyze a defined set of basic accessibility concerns.
4. Show findings with severity, evidence, and remediation category.
5. Offer only allowlisted deterministic fixes for automatic application.
6. Generate limited suggestions for ambiguous issues.
7. Require the user to approve or reject each ambiguous suggestion.
8. Apply approved changes to a copy, preserving the original.
9. Export the remediated PDF and a change summary.
10. Re-run supported validation and report remaining issues and limitations.

## Included capabilities

### Deterministic analysis and fixes

Initial candidates, subject to technical validation:

| Candidate | Analyze | Auto-fix | Condition |
| --- | --- | --- | --- |
| Document title metadata | Yes | Yes | A user-supplied title is available and the write is verifiably safe. |
| Display document title preference | Yes | Yes | The PDF catalog supports a safe deterministic update. |
| Document language metadata | Yes | Yes | The user supplies or confirms a valid language value. |
| Presence of encryption or processing restrictions | Yes | No | Report and stop or limit processing as appropriate. |
| Basic tagged/untagged status | Yes | No | Report only; do not claim that “tagged” means accessible. |

The final auto-fix allowlist will contain only changes proven safe through experiments and tests. No change becomes automatic merely because an AI model expresses confidence.

### Human-review suggestions

V1 will demonstrate a small, bounded set selected after feasibility spikes. Candidates include:

- Suggested alternative text for selected figures that already have identifiable tag targets.
- Suggested heading-level corrections for clearly exposed structure elements.
- Plain-language explanations of findings and recommended manual follow-up.

Suggestions must include the affected item, current state, proposed state, rationale, confidence or uncertainty, and an approve/reject control. Rejection leaves the document unchanged.

### Validation and output

- Re-run the application's supported checks after changes.
- Use veraPDF where practical and label exactly what profile or checks were run.
- Treat PAC as an external/manual acceptance test, not an automated guarantee.
- Export a new PDF; never overwrite the uploaded original.
- Produce a change summary separating applied automatic fixes, approved suggestions, rejected suggestions, unresolved findings, and validation limitations.

## Acceptance criteria

- The complete workflow succeeds on a small synthetic/public reference corpus.
- Invalid and unsupported files fail safely with useful messages.
- Original files remain unchanged.
- Every mutation appears in the change summary.
- Ambiguous changes cannot be applied without explicit approval.
- Revalidation results do not imply guarantees beyond the checks actually performed.
- Tests demonstrate that the automatic-fix allowlist cannot silently expand.
- Documentation states the data boundary and product disclaimer prominently.

## Assumptions

- V1 uses a simple server-rendered or minimal web interface; visual polish is secondary to clarity.
- Documents are processed temporarily for a single session.
- The reference corpus can be built entirely from synthetic or public PDFs.
- V1 favors depth on a few remediations over shallow support for many issue types.

## Non-goals

- Automatic repair of full tag trees, complex reading order, tables, forms, lists, OCR, or scanned documents.
- Bulk processing, shared workspaces, accounts, audit retention, or real-time collaboration.
- A compliance score that reduces accessibility to one number.
- Uploading workplace or sensitive documents without explicit approval.
- PAC automation or reverse engineering.

## Open questions

- Which suggestion types can be safely mapped back to editable PDF objects?
- Should document language and title be user-confirmed inputs rather than inferred values?
- Which veraPDF checks align with the limited V1 claims?
- What constitutes a supported PDF versus a safe report-only PDF?
- What minimum change history is needed for a convincing portfolio demonstration?

## Deferred decisions

- Exact upload limits and session timeout.
- Exact V1 suggestion set until spikes establish feasibility.
- AI provider, prompt format, and cost controls.
- Accessibility and usability requirements for the application interface itself beyond a strong accessible baseline.
