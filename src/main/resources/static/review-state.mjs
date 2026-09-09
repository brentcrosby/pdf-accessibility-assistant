export const decisionLabels = Object.freeze({
    KEEP_AS_CONTENT: 'Keep as content', ARTIFACT_CANDIDATE: 'Artifact candidate', DEFER: 'Defer'
});

export function filterRegions(regions, {kind = 'ALL', search = '', status = 'ALL'} = {}, decisions = new Map()) {
    const query = search.trim().toLowerCase();
    return regions.filter(region => (kind === 'ALL' || region.kind === kind)
        && (!query || `${region.text} ${region.id}`.toLowerCase().includes(query))
        && (status === 'ALL' || (status === 'UNREVIEWED' ? !decisions.has(region.id)
            : status === 'UNMAPPED' ? region.unmappedGlyphs > 0 : decisions.get(region.id)?.decision === status)));
}

export class ReviewSession {
    constructor(documentSnapshot) {
        this.source = structuredClone({documentId: documentSnapshot.id, originalFilename: documentSnapshot.originalFilename,
            originalSha256: documentSnapshot.originalSha256, pageCount: documentSnapshot.analysis.pageCount});
        this.pages = new Map();
        this.decisions = new Map();
        this.history = [];
    }
    register(page) { this.pages.set(page.pageNumber, structuredClone(page)); }
    region(id) {
        for (const page of this.pages.values()) {
            const match = page.regions.find(region => region.id === id);
            if (match) return match;
        }
    }
    apply(ids, decision, note) {
        if (!Object.hasOwn(decisionLabels, decision)) throw new Error('Unknown review decision.');
        const unique = [...new Set(ids)];
        if (!unique.length) return 0;
        const regions = unique.map(id => {
            const region = this.region(id);
            if (!region) throw new Error('Select a region from this document.');
            return region;
        });
        if (note !== undefined && note.length > 1000) throw new Error('Notes must be 1,000 characters or fewer.');
        const before = unique.map(id => [id, structuredClone(this.decisions.get(id))]);
        const reviewedAt = new Date().toISOString();
        for (const region of regions) {
            this.decisions.set(region.id, {region: structuredClone(region), decision,
                note: note === undefined ? (this.decisions.get(region.id)?.note || '') : note.trim(),
                reviewedAt, geometryVersion: this.pages.get(region.pageNumber).geometryVersion, appliedToPdf: false});
        }
        this.history.push(before);
        if (this.history.length > 50) this.history.shift();
        return unique.length;
    }
    remove(id) {
        if (!this.decisions.has(id)) return;
        this.history.push([[id, structuredClone(this.decisions.get(id))]]);
        if (this.history.length > 50) this.history.shift();
        this.decisions.delete(id);
    }
    undo() {
        const before = this.history.pop();
        if (!before) return false;
        for (const [id, entry] of before) {
            if (entry) this.decisions.set(id, entry);
            else this.decisions.delete(id);
        }
        return true;
    }
    report() {
        return structuredClone({schemaVersion: '2.0', reportType: 'review-plan', ...this.source,
            generatedAt: new Date().toISOString(), appliedToPdf: false,
            scope: 'Only pages explicitly loaded in this browser session were inspected.',
            pagesInspected: [...this.pages.values()].map(page => ({pageNumber: page.pageNumber,
                rotation: page.rotation, width: page.width, height: page.height, geometryVersion: page.geometryVersion,
                regionCount: page.regions.length, truncated: page.truncated, warnings: page.warnings})),
            reviewerDecisions: [...this.decisions.values()],
            limitation: 'Review decisions are proposals only and do not apply changes. Existing repairs in the uploaded source are not enumerated by this plan. '
                + 'Tagging, figure alt text and reading order were not evaluated. This is not a compliance result.'});
    }
}
