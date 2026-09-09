import {ReviewSession, decisionLabels, filterRegions} from './review-state.mjs';
import {ArtifactRepairs} from './artifact-repair.mjs';

const $ = selector => document.querySelector(selector);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
const announce = text => { $('#status').textContent = text; };
const blobUrls = new Set();
const exports = [];
let current;
let view;
let exportBusy = false;
let preparedReportUrl;
const repairs = new ArtifactRepairs({context:()=>current, checkedFetch, objectUrl, jsonUrl,
    isBusy:()=>exportBusy, setBusy:busy=>{ exportBusy = busy; $('#export').disabled = busy; $('#submit').disabled = busy; },
    continueWith:async entry=>{
        const form = new FormData(); form.append('file',entry.pdf,entry.filename); form.append('sourceType',entry.sourceType || 'SYNTHETIC');
        const snapshot = await (await checkedFetch('/api/documents',{method:'POST',body:form})).json();
        view?.dispose(); current = {snapshot,review:new ReviewSession(snapshot)}; showDocument();
        await view.load(entry.evidence.region.pageNumber); $('#result').scrollIntoView({block:'start',behavior:'smooth'});
    }});

function objectUrl(blob) {
    const url = URL.createObjectURL(blob);
    blobUrls.add(url);
    return url;
}
function release(url) { if (url) { URL.revokeObjectURL(url); blobUrls.delete(url); } }
function jsonUrl(report) { return objectUrl(new Blob([JSON.stringify(report, null, 2)], {type: 'application/json'})); }
function downloadReport(report, filename) {
    release(preparedReportUrl);
    preparedReportUrl = jsonUrl(report);
    const a = document.createElement('a');
    a.href = preparedReportUrl; a.download = filename; a.textContent = 'Download review report (JSON)';
    $('#report-download').replaceChildren(a);
    $('#report-preview').textContent = JSON.stringify(report, null, 2);
    $('#report-details').hidden = false;
    announce('Review report prepared. Use the download link to save this snapshot.');
}
async function checkedFetch(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || `Request failed (${response.status}). Try again.`);
    }
    return response;
}

$('#upload-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (exportBusy) return;
    $('#submit').disabled = true;
    view?.dispose();
    $('#result').hidden = true;
    announce('Analyzing PDF…');
    try {
        const response = await checkedFetch('/api/documents', {method: 'POST', body: new FormData(event.currentTarget)});
        const snapshot = await response.json();
        current = {snapshot, review: new ReviewSession(snapshot)};
        showDocument();
        announce('Analysis complete. Loading the first page…');
        await view.load(1);
    } catch (error) {
        announce(error.message || 'The local server could not be reached.');
    } finally { $('#submit').disabled = false; }
});

function showDocument() {
    repairs.reset();
    const d = current.snapshot, a = d.analysis;
    $('#result').hidden = false;
    $('#document-name').textContent = d.originalFilename;
    $('#document-summary').textContent = `${a.pageCount} pages · Title: ${a.title || 'missing'} · Language: ${a.language || 'missing'} · Marked: ${a.markedAsTagged} · Structure tree: ${a.structureTreePresent}`;
    $('#source-identity').textContent = JSON.stringify(current.review.source, null, 2);
    $('#export-result').replaceChildren();
    release(preparedReportUrl); preparedReportUrl = null;
    $('#report-download').replaceChildren(); $('#report-details').hidden = true; $('#report-preview').textContent = '';
    showFindings();
    view = new Workbench(current);
}

function showFindings() {
    const d = current.snapshot;
    $('#findings-summary').textContent = `${d.analysis.issues.length} document findings · Metadata and tagging signals`;
    $('#findings').innerHTML = d.analysis.issues.map(issue => {
        const review = d.recordedReviews[issue.code];
        return `<article class="issue"><span class="tag">${escape(issue.disposition)}</span><h3>${escape(issue.code)}</h3>
            <p>${escape(issue.message)}</p><p>${escape(issue.recommendedAction)}</p>
            ${review ? `<p>Recorded review: ${escape(review.decision)} ${escape(review.value || '')}</p>` : ''}
            ${issue.requiresHumanValue ? `<form data-review="${escape(issue.code)}"><label>Reviewed value for ${escape(issue.code)}<input name="value" maxlength="250" value="${escape(review?.value || '')}"></label><button type="submit" name="decision" value="APPROVE">Approve value</button><button type="submit" name="decision" value="REJECT" class="secondary">Reject</button></form>` : ''}</article>`;
    }).join('') || '<p>No bounded metadata findings were detected.</p>';
}

$('#findings').addEventListener('submit', async event => {
    event.preventDefault();
    const target = current;
    const form = event.target;
    const decision = event.submitter?.value || 'APPROVE';
    for (const b of form.querySelectorAll('button')) b.disabled = true;
    try {
        const response = await checkedFetch(`/api/documents/${target.snapshot.id}/reviews`, {method:'POST',
            headers:{'Content-Type':'application/json'}, body:JSON.stringify({issueCode:form.dataset.review, decision, value:form.elements.value.value})});
        target.snapshot = await response.json();
        if (current === target) { showFindings(); announce('Metadata review recorded.'); }
    } catch (error) { announce(error.message); }
    finally { for (const b of form.querySelectorAll('button')) b.disabled = false; }
});

class Workbench {
    constructor(doc) {
        this.doc = doc;
        this.session = doc.review;
        this.page = null;
        this.number = 1;
        this.selected = new Set();
        this.focused = null;
        this.drafts = new Map();
        this.generation = 0;
        this.previewUrl = null;
        this.imageReady = false;
        this.events = new AbortController();
        const on = (selector, event, fn) => $(selector).addEventListener(event, fn, {signal:this.events.signal});
        $('#page-number').max = doc.snapshot.analysis.pageCount;
        $('#page-total').textContent = `/ ${doc.snapshot.analysis.pageCount}`;
        $('#kind').value = 'ALL'; $('#review-filter').value = 'ALL'; $('#search').value = ''; $('#zoom').value = '1';
        $('#show-overlays').checked = true;
        $('#page-canvas').style.width = '100%';
        on('#next-page', 'click', () => this.load(this.number + 1));
        on('#previous-page', 'click', () => this.load(this.number - 1));
        on('#page-number', 'change', () => this.load(Number($('#page-number').value)));
        on('#zoom', 'change', () => { $('#page-canvas').style.width = `${Number($('#zoom').value) * 100}%`; });
        on('#show-overlays', 'change', () => this.renderRegions());
        for (const selector of ['#kind', '#review-filter', '#search']) on(selector, selector === '#search' ? 'input' : 'change', () => {
            this.selected.clear(); this.focused = null; this.render();
        });
        on('#select-visible', 'click', () => { this.selected = new Set(this.visible().map(r => r.id)); this.render(); });
        on('#clear-selection', 'click', () => { this.selected.clear(); this.focused = null; this.render(); });
        on('#reset-filters', 'click', () => {
            $('#kind').value = 'ALL'; $('#review-filter').value = 'ALL'; $('#search').value = '';
            this.selected.clear(); this.focused = null; this.render();
        });
        on('#region-list', 'change', event => {
            const id = event.target.dataset.check;
            if (!id) return;
            if (event.target.checked) this.selected.add(id); else this.selected.delete(id);
            this.focused = id; this.renderDetails(); this.renderRegions(false); this.updateActions();
        });
        on('#region-list', 'click', event => {
            const button = event.target.closest('[data-region]');
            if (button) this.select(button.dataset.region, true);
        });
        on('#overlays', 'click', event => {
            const button = event.target.closest('[data-region]');
            if (button) this.select(button.dataset.region, false);
        });
        on('#decisions', 'click', event => {
            const decision = event.target.dataset.decision;
            if (!decision) return;
            const count = this.session.apply([...this.selected], decision);
            this.selected.clear(); this.render();
            announce(`${decisionLabels[decision]} recorded for ${count} region(s). PDF unchanged.`);
        });
        on('#selected-region', 'input', event => {
            if (event.target.id === 'region-note') this.drafts.set(this.focused, event.target.value);
        });
        on('#selected-region', 'click', event => {
            if (event.target.id === 'prepare-artifact') {
                const region = this.page?.regions.find(r=>r.id === this.focused);
                if (region && this.selected.size === 1 && this.selected.has(region.id)) repairs.prepare(region,this.page);
                return;
            }
            if (event.target.id !== 'save-note') return;
            const previous = this.session.decisions.get(this.focused);
            this.session.apply([this.focused], previous?.decision || 'DEFER', this.drafts.get(this.focused) || '');
            this.drafts.delete(this.focused); this.render(); announce('Note saved in the review plan.');
        });
        on('#undo', 'click', () => { if (this.session.undo()) { this.drafts.clear(); this.render(); announce('Last review change undone.'); } });
        on('#queue', 'click', async event => {
            const remove = event.target.closest('[data-remove]');
            if (remove) { this.session.remove(remove.dataset.remove); this.render(); return; }
            const jump = event.target.closest('[data-jump]');
            if (jump) {
                const entry = this.session.decisions.get(jump.dataset.jump);
                $('#kind').value = 'ALL'; $('#review-filter').value = 'ALL'; $('#search').value = '';
                await this.load(entry.region.pageNumber);
                if (!this.events.signal.aborted && this.page) this.select(entry.region.id, true);
            }
        });
        on('#download-report', 'click', () => downloadReport(this.session.report(), `${doc.snapshot.originalFilename.replace(/\.pdf$/i, '')}-review-plan.json`));
        this.render();
    }

    dispose() { this.generation++; this.request?.abort(); this.events.abort(); release(this.previewUrl); }

    async load(number) {
        if (!Number.isInteger(number) || number < 1 || number > this.doc.snapshot.analysis.pageCount) {
            $('#page-number').value = this.number;
            announce('Choose an existing page number.'); return;
        }
        this.request?.abort(); this.request = new AbortController();
        const generation = ++this.generation;
        const stale = () => generation !== this.generation || this.events.signal.aborted;
        this.number = number; this.page = null; this.selected.clear(); this.focused = null; this.imageReady = false;
        $('#page-number').value = number;
        $('#previous-page').disabled = number <= 1; $('#next-page').disabled = number >= this.doc.snapshot.analysis.pageCount;
        $('#page-canvas').hidden = true;
        $('#page-status').textContent = `Loading page ${number}…`;
        $('#page-scroll').scrollTop = 0; $('#page-scroll').scrollLeft = 0;
        release(this.previewUrl); this.previewUrl = null;
        this.render();
        const base = `/api/documents/${this.doc.snapshot.id}/pages/${number}`;
        try {
            const page = this.session.pages.get(number) || await (await checkedFetch(`${base}/observations`, {signal:this.request.signal})).json();
            if (stale()) return;
            this.page = page; this.session.register(page); this.render();
            $('#page-status').textContent = `Rendering page ${number}…`;
            const imageResponse = await checkedFetch(`${base}/preview`, {signal:this.request.signal});
            const blob = await imageResponse.blob();
            if (stale()) return;
            this.previewUrl = objectUrl(blob);
            const img = $('#page-image');
            img.src = this.previewUrl; img.alt = `Original page ${number}; inspect content using the region list.`;
            await img.decode();
            if (stale()) return;
            this.imageReady = true; $('#page-canvas').hidden = false;
            $('#page-status').textContent = `Page ${number} · ${page.rotation}° rotation · ${page.regions.length} observations${page.truncated ? ' (partial)' : ''}`;
            this.renderRegions();
            announce(`Page ${number} ready for review.`);
        } catch (error) {
            if (stale() || error.name === 'AbortError') return;
            $('#page-status').textContent = error.message || 'The page could not be loaded.';
            const retry = document.createElement('button'); retry.textContent = 'Retry page'; retry.className = 'secondary';
            retry.onclick = () => this.load(number); $('#page-status').append(' ', retry);
        }
    }

    visible() {
        return filterRegions(this.page?.regions || [], {kind:$('#kind').value, search:$('#search').value, status:$('#review-filter').value}, this.session.decisions);
    }

    select(id, fromList) {
        this.focused = id; this.selected = new Set([id]); this.render();
        $('#region-list').querySelector(`[data-region="${id}"]`)?.focus({preventScroll:true});
        if (fromList) $('#overlays').querySelector(`[data-region="${id}"]`)?.scrollIntoView({block:'nearest', inline:'nearest'});
    }

    render() { this.renderRegions(); this.renderDetails(); this.renderQueue(); this.updateActions(); }

    renderRegions(rebuildList = true) {
        const regions = this.visible();
        $('#region-count').textContent = this.page ? `${regions.length} shown / ${this.page.regions.length} observed · ${this.selected.size} selected` : 'Waiting for page content…';
        $('#select-visible').textContent = `Select visible (${regions.length})`;
        $('#select-visible').disabled = !regions.length;
        $('#clear-selection').disabled = !this.selected.size && !this.focused;
        if (rebuildList) {
            $('#region-list').innerHTML = regions.map(r => `<li><input type="checkbox" data-check="${r.id}" aria-label="Include ${escape(r.id)} in bulk review" ${this.selected.has(r.id) ? 'checked' : ''}><button type="button" class="region-link" data-region="${r.id}" aria-pressed="${this.focused === r.id}"><span class="kind-label">${escape(r.kind)} · ${escape(r.id)} · ${escape(decisionLabels[this.session.decisions.get(r.id)?.decision] || 'Unreviewed')}</span>${escape(r.text || 'Text without a Unicode mapping')}</button></li>`).join('')
                || `<li class="muted">${this.page && !this.page.regions.length ? 'No supported visible regions found. Image-only pages have no selectable OCR text.' : 'No content matches these filters.'}</li>`;
        }
        $('#overlays').hidden = !this.imageReady || !$('#show-overlays').checked;
        // Large background paths are inserted first so smaller regions remain clickable.
        $('#overlays').innerHTML = [...regions].sort((a,b) => b.bounds.width*b.bounds.height-a.bounds.width*a.bounds.height).map(r => {
            const b = r.bounds;
            return `<button class="overlay" type="button" data-region="${r.id}" data-kind="${r.kind}" aria-label="Inspect ${escape(r.kind.toLowerCase())}: ${escape(r.text)}" aria-pressed="${this.focused === r.id || this.selected.has(r.id)}" title="${escape(r.text)}" style="left:${b.x*100}%;top:${b.y*100}%;width:${b.width*100}%;height:${b.height*100}%"></button>`;
        }).join('');
        $('#page-warnings').innerHTML = (this.page?.warnings || []).map(w => `<li>${escape(w)}</li>`).join('');
    }

    renderDetails() {
        const region = this.page?.regions.find(r => r.id === this.focused);
        if (!region) { $('#selected-region').innerHTML = '<p>Select a region to view its details and add a note.</p>'; return; }
        const entry = this.session.decisions.get(region.id);
        const note = this.drafts.get(region.id) ?? entry?.note ?? '';
        $('#selected-region').innerHTML = `<h3>${escape(region.kind)} · ${escape(region.id)}</h3><p>${escape(region.text)}</p>
            <p>Bounds: ${escape(region.geometryQuality.toLowerCase().replaceAll('_',' '))}. Tagging: not evaluated.</p>
            ${region.kind === 'IMAGE' ? '<p>An image observation does not establish a semantic figure or missing alt text.</p>' : ''}
            ${region.unmappedGlyphs ? `<p>${region.unmappedGlyphs} glyph(s) have no Unicode mapping. This is a diagnostic signal, not a deletion recommendation.</p>` : ''}
            <p>Decision: ${escape(decisionLabels[entry?.decision] || 'Unreviewed')}</p>
            ${region.kind === 'PATH' ? `<button id="prepare-artifact" type="button" ${this.selected.size === 1 && this.selected.has(region.id) ? '' : 'disabled'}>Prepare artifact repair</button><p class="muted">Check whether this path can be repaired and exported. Select one path at a time.</p>` : ''}
            <label for="region-note">Review note (up to 1,000 characters)</label><textarea id="region-note" maxlength="1000">${escape(note)}</textarea><button id="save-note" type="button" class="secondary">Save note</button><p class="muted">Saving a note on an unreviewed region adds it to the queue as deferred.</p>`;
    }

    updateActions() {
        $('#decisions').disabled = !this.selected.size;
        $('#decision-target').textContent = `Review ${this.selected.size} selected region(s) on page ${this.number}`;
        $('#undo').disabled = !this.session.history.length;
    }

    renderQueue() {
        const entries = [...this.session.decisions.values()];
        $('#queue-summary').textContent = `${entries.length} proposed decision(s) across ${new Set(entries.map(e => e.region.pageNumber)).size} page(s). ${this.session.pages.size} / ${this.doc.snapshot.analysis.pageCount} pages inspected. PDF unchanged.`;
        $('#queue').innerHTML = entries.map(entry => `<li><div class="queue-row"><span><strong>${escape(decisionLabels[entry.decision])}</strong> · Page ${entry.region.pageNumber} · ${escape(entry.region.kind)}<br>${escape(entry.region.text)}</span><button type="button" class="secondary" data-jump="${entry.region.id}">Locate ${entry.region.id}</button><button type="button" class="secondary" data-remove="${entry.region.id}" aria-label="Remove decision ${entry.region.id}">Remove</button></div>${entry.note ? `<p class="queue-note">${escape(entry.note)}</p>` : ''}</li>`).join('');
    }
}

$('#export').addEventListener('click', async () => {
    if (!current || exportBusy) return;
    const target = current;
    // Capture review evidence when export is requested, even if review decisions later change.
    const reviewPlan = target.review.report();
    exportBusy = true; $('#export').disabled = true; $('#submit').disabled = true;
    announce('Creating and revalidating a separate PDF copy…');
    try {
        const response = await checkedFetch(`/api/documents/${target.snapshot.id}/export`, {method:'POST'});
        const pdfUrl = objectUrl(await response.blob());
        const headers = response.headers;
        const filename = target.snapshot.originalFilename.replace(/\.pdf$/i,'') + '-remediated.pdf';
        const actions = (headers.get('X-Remediation-Actions') || '').split(',').filter(Boolean);
        const issues = (headers.get('X-Revalidation-Issue-Codes') || '').split(',').filter(Boolean);
        const bool = key => headers.has(key) ? headers.get(key) === 'true' : null;
        const record = {schemaVersion:'2.0', originalFilename:target.snapshot.originalFilename,
            originalSha256:target.snapshot.originalSha256, exportFilename:filename, exportedAt:new Date().toISOString(),
            appliedActions:actions, remainingBoundedFindings:issues,
            revalidation:{pageCount:headers.has('X-Revalidation-Page-Count') ? Number(headers.get('X-Revalidation-Page-Count')) : null,
                encrypted:bool('X-Revalidation-Encrypted'), titlePresent:bool('X-Revalidation-Title-Present'), languagePresent:bool('X-Revalidation-Language-Present'),
                displayDocumentTitle:bool('X-Revalidation-Display-Document-Title'), marked:bool('X-Revalidation-Marked'), structureTree:bool('X-Revalidation-Structure-Tree')},
            reviewPlan, limitation:'Only appliedActions describes changes to the PDF. Region decisions are proposals. This is not a compliance result.'};
        const entry = {pdfUrl, filename, recordUrl:jsonUrl(record), record};
        exports.unshift(entry);
        const links = item => `<a href="${item.pdfUrl}" download="${escape(item.filename)}">Download remediated copy</a><a href="${item.recordUrl}" download="${escape(item.filename.replace(/\.pdf$/i,'') + '-remediation-record.json')}">Download remediation record (JSON)</a>`;
        $('#export-result').innerHTML = `<p>Applied: ${escape(actions.join(', ') || 'No supported changes needed')}. Remaining bounded findings: ${escape(issues.join(', ') || 'None detected')}.</p>${links(entry)}`;
        $('#export-history').hidden = false;
        $('#export-history').innerHTML = `<h2>Export history</h2><p class="muted">Available until this page is reloaded.</p><ol>${exports.map(item => `<li><strong>${escape(item.filename)}</strong> · ${escape(new Date(item.record.exportedAt).toLocaleString())}<br>${links(item)}</li>`).join('')}</ol>`;
        announce('Export complete. Your original PDF remains unchanged.');
    } catch (error) { announce(error.message); }
    finally { exportBusy = false; $('#export').disabled = false; $('#submit').disabled = false; }
});

window.addEventListener('beforeunload', () => { view?.dispose(); for (const url of blobUrls) URL.revokeObjectURL(url); });
