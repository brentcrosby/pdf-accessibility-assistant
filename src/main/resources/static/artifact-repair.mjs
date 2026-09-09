const $ = selector => document.querySelector(selector);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fromBase64 = (value, type) => new Blob([Uint8Array.from(atob(value), c => c.charCodeAt(0))], {type});

export class ArtifactRepairs {
    constructor(deps) { this.deps = deps; this.generation = 0; this.entries = []; }
    reset() { this.generation++; $('#artifact-panel').hidden = true; $('#artifact-panel').replaceChildren(); }

    async prepare(region, page) {
        if (this.deps.isBusy()) return;
        const source = this.deps.context();
        const generation = ++this.generation;
        const request = {pageNumber:page.pageNumber, regionId:region.id,
            originalSha256:source.snapshot.originalSha256, geometryVersion:page.geometryVersion, decorativeConfirmed:false};
        const panel = $('#artifact-panel'); panel.hidden = false;
        panel.innerHTML = '<h2>Repair a decorative path</h2><p role="status">Checking the selected drawing…</p>';
        panel.scrollIntoView({block:'start',behavior:'smooth'});
        try {
            const response = await this.deps.checkedFetch(`/api/documents/${source.snapshot.id}/artifact-repairs/check`, {
                method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});
            const check = await response.json();
            if (generation !== this.generation || source !== this.deps.context()) return;
            panel.innerHTML = `<h2>Repair a decorative path</h2><p><strong>${escape(source.snapshot.originalFilename)}</strong> · Page ${page.pageNumber} · ${escape(region.id)} · ${escape(region.text)}</p>
                <p role="status">${escape(check.reason)}</p>${check.eligible ? `<p>Marking a path as an artifact tells assistive technology it is decoration. Its visible appearance stays the same. Do not use this for charts, symbols or other meaningful graphics.</p>
                <form id="artifact-form"><label class="check-label"><input id="artifact-confirm" type="checkbox" required> This path is decorative and conveys no information.</label>
                <label for="artifact-note">Repair note</label><textarea id="artifact-note" maxlength="1000">${escape(source.review.decisions.get(region.id)?.note || '')}</textarea>
                <button id="apply-artifact" type="submit" disabled>Apply artifact and export PDF</button></form>
                <p class="muted">This export applies one path. Other decisions and metadata reviews are saved in the repair record for reference.</p><p id="artifact-status" role="status"></p>` : ''}`;
            if (!check.eligible) return;
            $('#artifact-confirm').addEventListener('change', () => { $('#apply-artifact').disabled = !$('#artifact-confirm').checked || this.deps.isBusy(); });
            $('#artifact-form').addEventListener('submit', event => { event.preventDefault(); this.apply(source,request,generation); });
            $('#artifact-confirm').focus({preventScroll:true});
        } catch (error) {
            if (generation === this.generation) panel.innerHTML = `<h2>Repair unavailable</h2><p role="status">${escape(error.message)}</p><p>Select the path again to retry.</p>`;
        }
    }

    async apply(source, request, generation) {
        if (this.deps.isBusy() || !$('#artifact-confirm').checked || source !== this.deps.context()) return;
        const payload = {...request, decorativeConfirmed:true, note:$('#artifact-note').value};
        const reviewPlan = source.review.report(), metadataReviews = structuredClone(source.snapshot.recordedReviews);
        this.deps.setBusy(true);
        $('#apply-artifact').disabled = true; $('#artifact-confirm').disabled = true; $('#artifact-note').disabled = true;
        $('#artifact-status').textContent = 'Writing the copy and checking its appearance, text and document structure…';
        try {
            const response = await this.deps.checkedFetch(`/api/documents/${source.snapshot.id}/artifact-repairs`, {
                method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
            const result = await response.json();
            if (generation !== this.generation || source !== this.deps.context()) return;
            const evidence = {...result.evidence, reviewPlan, recordedMetadataReviews:metadataReviews};
            const pdf = fromBase64(result.pdf,'application/pdf');
            const entry = {pdf,filename:evidence.outputFilename, sourceType:source.snapshot.sourceType, evidence,
                pdfUrl:this.deps.objectUrl(pdf), recordUrl:this.deps.jsonUrl(evidence),
                beforeUrl:this.deps.objectUrl(fromBase64(result.beforePreview,'image/png')),
                afterUrl:this.deps.objectUrl(fromBase64(result.afterPreview,'image/png'))};
            this.entries.unshift(entry); this.renderHistory();
            $('#artifact-panel').innerHTML = `<h2>Artifact repair complete</h2><p role="status">Page ${request.pageNumber}: ${escape(request.regionId)} is now marked as an artifact in the exported copy. Verification passed.</p><p>The download and comparison are in Repair history below.</p>`;
            $('#artifact-history').scrollIntoView({block:'start',behavior:'smooth'});
        } catch (error) {
            $('#artifact-status').textContent = error.message;
            $('#artifact-confirm').disabled = false; $('#artifact-note').disabled = false;
            $('#apply-artifact').disabled = !$('#artifact-confirm').checked;
        } finally { this.deps.setBusy(false); }
    }

    renderHistory() {
        const history = $('#artifact-history'); history.hidden = false;
        history.innerHTML = `<h2>Repair history</h2><p>Each copy below contains a verified artifact repair. Downloads remain available until reload.</p>` + this.entries.map((entry,index) => `
            <article class="repair-entry"><h3>${escape(entry.filename)}</h3><p>Page ${entry.evidence.region.pageNumber} · ${escape(entry.evidence.region.id)} marked as an artifact. Preview pixels, extracted page text and the remaining document structure matched.</p>
            <div class="repair-links"><a href="${entry.pdfUrl}" download="${escape(entry.filename)}">Download repaired PDF</a><a href="${entry.recordUrl}" download="${escape(entry.filename.replace(/\.pdf$/i,'-repair-record.json'))}">Download repair record (JSON)</a></div>
            <details><summary>Compare before and after</summary><div class="repair-comparison"><figure><figcaption>Before repair · Page ${entry.evidence.region.pageNumber}</figcaption><img src="${entry.beforeUrl}" alt="Page before artifact repair"></figure><figure><figcaption>After repair · Page ${entry.evidence.region.pageNumber}</figcaption><img src="${entry.afterUrl}" alt="Page after artifact repair; preview pixels verified unchanged"></figure></div></details>
            <details><summary>Inspect repair evidence</summary><pre>${escape(JSON.stringify(entry.evidence,null,2))}</pre></details>
            <button type="button" data-continue-repair="${index}">Continue with this repaired copy</button><p class="muted">Starts a fresh review session for this copy so additional repairs can build on it. The previous review plan is included in this repair's JSON record.</p><p role="status" data-continue-status="${index}"></p></article>`).join('');
        for (const button of history.querySelectorAll('[data-continue-repair]')) button.addEventListener('click', async () => {
            if (this.deps.isBusy()) return;
            const entry = this.entries[Number(button.dataset.continueRepair)];
            const status = history.querySelector(`[data-continue-status="${button.dataset.continueRepair}"]`);
            this.deps.setBusy(true); button.disabled = true; status.textContent = 'Opening the repaired copy…';
            try { await this.deps.continueWith(entry); status.textContent = 'Repaired copy opened in the workbench.'; }
            catch (error) { status.textContent = error.message; }
            finally { this.deps.setBusy(false); button.disabled = false; }
        });
    }
}
