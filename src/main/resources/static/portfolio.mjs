import {RepairQueue,moveSibling} from './repair-queue.mjs';
const $=s=>document.querySelector(s);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const blob=(s,type)=>new Blob([Uint8Array.from(atob(s),c=>c.charCodeAt(0))],{type});
const names={ALT_TEXT:'Figure alternative text',READING_ORDER:'Reading order',TAG_TEXT:'Accepted text tag',ARTIFACT:'Decorative path artifact',LANGUAGE:'Document language',DISPLAY_TITLE:'Display document title'};

export class Portfolio {
    constructor(deps) {
        this.deps=deps;this.generation=0;this.exports=[];
        $('#run-benchmark').addEventListener('click',()=>this.benchmark());
        $('#comparison-file').addEventListener('change',async e=>{
            const file=e.target.files[0];if(!file)return;
            try{if(file.size>100000)throw new Error('Comparison JSON must be below 100 KB.');await this.benchmark(JSON.parse(await file.text()));}
            catch(error){$('#benchmark-status').textContent=error.message;}
        });
        $('#semantic-content').addEventListener('click',event=>this.action(event));
        $('#semantic-content').addEventListener('change',event=>{if(event.target.id==='order-parent')this.renderOrder();});
        $('#semantic-retry').addEventListener('click',()=>this.open(this.doc));
        $('#repair-queue').addEventListener('click',event=>{
            if(this.deps.isBusy())return;const remove=event.target.closest('[data-remove-repair]');
            if(remove){this.queue.remove(remove.dataset.removeRepair);this.renderQueue();$('#repair-undo').focus();}
        });
        $('#repair-undo').addEventListener('click',()=>{if(!this.deps.isBusy() && this.queue.undo()){this.renderQueue();$('#repair-status').textContent='Last queue change undone.';}});
        $('#verify-transaction').addEventListener('click',()=>this.export());
    }
    async open(doc) {
        this.doc=doc;this.data=null;this.queue=new RepairQueue(doc.snapshot.originalSha256);this.members=new Map();this.orders=new Map();this.selectedNode=null;
        const generation=++this.generation;$('#semantic-content').replaceChildren();$('#semantic-status').textContent='Mapping tags and preparing text proposals…';$('#repair-status').textContent='New source loaded. Its repair queue is empty.';$('#semantic-retry').hidden=true;this.renderQueue();
        try {
            const data=await(await this.deps.checkedFetch(`/api/documents/${doc.snapshot.id}/semantics`)).json();
            if(generation!==this.generation)return;this.data=data;this.members=new Map(data.structure.regions.map(m=>[m.region.id,m]));
            $('#semantic-status').textContent=`${data.structure.nodes.length} structure elements · ${data.structure.regions.filter(r=>r.status==='TAGGED').length} tagged observations · ${data.proposals.length} text proposals`;
            this.render();this.deps.refresh();
        }catch(error){if(generation===this.generation){$('#semantic-status').textContent=error.message;$('#semantic-retry').hidden=false;}}
    }
    status(id) {return this.members?.get(id)?.status || 'NOT_EVALUATED';}
    describe(id) {const m=this.members?.get(id);return m?`${m.status.toLowerCase().replaceAll('_',' ')}${m.role?' · '+m.role:''}. ${m.reason}`:'Semantic review is loading or unavailable.';}
    node(id){return this.data?.structure.nodes.find(n=>n.id===id);}
    label(id){const n=this.node(id);return n?`${n.role} ${n.id}`:id;}
    text(node) {return node.locations.map(l=>this.members.get(l.regionId)?.region.text||'').join(' ').slice(0,140);}
    render() {
        const {nodes,warnings}=this.data.structure;
        const parents=nodes.filter(n=>n.appendEligible);
        const tree=n=>`<li><button type="button" class="secondary" data-node="${n.id}">${esc(this.label(n.id))}</button><span class="muted">${n.locations.length} observations${n.issues.length?' · unresolved references':''}</span>${n.childIds.length?`<ul>${n.childIds.map(id=>tree(this.node(id))).join('')}</ul>`:''}</li>`;
        $('#semantic-content').innerHTML=`<div class="semantic-grid"><section aria-label="Tag tree"><h3>Tags and figures</h3>
            <p>Choose a tag to inspect its content. Its page locations are linked to the viewer.</p>
            <ul class="tag-list">${nodes.filter(n=>n.parentId==='root').map(tree).join('') || '<li>No structure children. Accepted proposals can create a new Document when no root exists.</li>'}</ul><div id="node-details"></div></section>
            <section aria-label="Reading order editor"><h3>Reading order</h3><p>Move existing siblings within their parent. All children must remain in the sequence.</p>
            <label for="order-parent">Parent to reorder</label><select id="order-parent">${nodes.filter(n=>n.reorderable).map(n=>`<option value="${n.id}">${esc(this.label(n.id))}</option>`).join('') || '<option value="">No editable sibling groups</option>'}</select><div id="order-list"></div></section></div>
            <details><summary>Mapping coverage and unresolved references</summary><ul>${warnings.map(w=>`<li>${esc(w)}</li>`).join('')}${nodes.flatMap(n=>n.issues.map(i=>`<li>${esc(this.label(n.id))}: ${esc(i)}</li>`)).join('')}</ul></details>
            <section aria-label="Tagging proposals"><h3>Explainable tagging proposals</h3><p>Scores describe rule strength, not a calibrated probability. Review the role and content before queueing it.</p>
            <label for="tag-parent">Parent for accepted tags</label><select id="tag-parent">${parents.map(n=>`<option value="${n.id}">${esc(this.label(n.id))} — append as last child</option>`).join('') || (nodes.length?'<option value="">No supported parent; writing unavailable</option>':'<option value="auto">Create Document structure</option>')}</select>
            <div class="proposal-list">${this.data.proposals.map(p=>`<article class="proposal"><h4>${esc(p.text)}</h4><p>Page ${p.pageNumber} · Heuristic score ${Math.round(p.heuristicScore*100)}/100</p><ul>${p.reasons.map(r=>`<li>${esc(r)}</li>`).join('')}</ul>
            <label for="role-${p.id}">Role for ${esc(p.id)}</label><select id="role-${p.id}">${['H1','H2','H3','P','LI','Caption'].map(r=>`<option ${r===p.role?'selected':''}>${r}</option>`).join('')}</select>
            <button type="button" class="secondary" data-locate-proposal="${p.id}">Show proposal on page</button> <button type="button" data-accept-tag="${p.id}">Accept and queue tag</button></article>`).join('') || '<p>No eligible untagged text groups were found.</p>'}</div></section>
            <section aria-label="Metadata repair queue"><h3>Metadata in this export</h3><label for="transaction-language">Document language</label><input id="transaction-language" value="${esc(this.doc.snapshot.analysis.language||'')}" placeholder="en-US"><button type="button" data-queue-language>Queue language</button> <button type="button" class="secondary" data-queue-title>Queue display of existing title</button></section>`;
        this.renderOrder();this.selectNode(nodes.find(n=>n.altEditable)?.id || nodes.find(n=>n.id!=='root')?.id);
    }
    selectNode(id) {
        this.selectedNode=id;const node=this.node(id);if(!node){$('#node-details').replaceChildren();return;}
        $('#node-details').innerHTML=`<h4>${esc(this.label(id))}</h4><p>${esc(this.text(node))}</p><p>${node.locations.length} mapped page observation(s).</p>
            <button type="button" class="secondary" data-locate-node="${id}" ${node.locations.length?'':'disabled'}>Show tag on page</button>
            ${node.role==='Figure'?`<p>Current alternative text: ${esc(node.altText||'Missing')}</p><label for="figure-alt">Alternative text for ${esc(this.label(id))}</label><textarea id="figure-alt" maxlength="2000" ${node.altEditable?'':'disabled'}>${esc(this.queue.items.get('ALT_TEXT:'+id)?.value??node.altText??'')}</textarea><button type="button" data-queue-alt="${id}" ${node.altEditable?'':'disabled'}>Queue alternative text</button>`:''}
            ${node.issues.map(i=>`<p class="muted">${esc(i)}</p>`).join('')}`;
    }
    focusRegion(id) {
        let node=this.node(this.members.get(id)?.nodeId);if(!node)return;
        let ancestor=node;while(ancestor){if(ancestor.role==='Figure'){node=ancestor;break;}ancestor=this.node(ancestor.parentId);}
        this.selectNode(node.id);
    }
    renderOrder(focusId) {
        const parent=this.node($('#order-parent').value);if(!parent){$('#order-list').innerHTML='<p>Tag text or use a tagged demo to edit sibling order.</p>';return;}
        const order=this.orders.get(parent.id)||[...parent.childIds];this.orders.set(parent.id,order);
        $('#order-list').innerHTML=`<ol>${order.map((id,i)=>`<li><span>${esc(this.label(id))} · ${esc(this.text(this.node(id)))}</span><div>
            <button type="button" class="secondary" data-order-id="${id}" data-order-delta="-1" aria-label="Move ${esc(this.label(id))} up" ${i?'':'disabled'}>↑ Up</button>
            <button type="button" class="secondary" data-order-id="${id}" data-order-delta="1" aria-label="Move ${esc(this.label(id))} down" ${i===order.length-1?'disabled':''}>↓ Down</button>
            <button type="button" class="secondary" data-locate-node="${id}">Locate ${esc(this.label(id))}</button></div></li>`).join('')}</ol><button type="button" data-queue-order="${parent.id}">Queue reading order</button>`;
        if(focusId)$('#order-list').querySelector(`[data-order-id="${focusId}"]:not(:disabled)`)?.focus();
    }
    put(op) {this.queue.put({pageNumber:0,confirmed:true,...op});this.renderQueue();$('#repair-status').textContent=`${names[op.kind]} queued. Export to apply it.`;}
    queueArtifact(region) {try{this.put({kind:'ARTIFACT',targetId:region.id,pageNumber:region.pageNumber});}catch(error){$('#repair-status').textContent=error.message;}}
    async action(event) {
        const button=event.target.closest('button');if(!button || this.deps.isBusy())return;const d=button.dataset;
        try {
            if(d.node)this.selectNode(d.node);
            if(d.locateNode)await this.deps.locate(this.node(d.locateNode).locations);
            if(d.locateProposal){const p=this.data.proposals.find(p=>p.id===d.locateProposal);await this.deps.locate(p.regionIds.map(id=>({pageNumber:p.pageNumber,regionId:id})));}
            if(d.queueAlt){const value=$('#figure-alt').value.trim();if(!value)throw new Error('Enter meaningful alternative text before queueing.');this.put({kind:'ALT_TEXT',targetId:d.queueAlt,value});}
            if(d.orderId){const parent=$('#order-parent').value;this.orders.set(parent,moveSibling(this.orders.get(parent),d.orderId,Number(d.orderDelta)));this.renderOrder(d.orderId);$('#repair-status').textContent=`Moved ${this.label(d.orderId)}. Queue the order to include it in the export.`;}
            if(d.queueOrder)this.put({kind:'READING_ORDER',targetId:d.queueOrder,order:this.orders.get(d.queueOrder)});
            if(d.acceptTag)this.put({kind:'TAG_TEXT',targetId:d.acceptTag,value:$(`#role-${d.acceptTag}`).value,parentId:$('#tag-parent').value});
            if('queueLanguage' in d)this.put({kind:'LANGUAGE',targetId:'document',value:$('#transaction-language').value.trim()});
            if('queueTitle' in d)this.put({kind:'DISPLAY_TITLE',targetId:'document'});
        }catch(error){$('#repair-status').textContent=error.message;}
    }
    renderQueue() {
        $('#repair-queue').innerHTML=[...this.queue.items].map(([key,op])=>`<li><strong>${esc(names[op.kind])}</strong> · ${esc(op.targetId)}<p>${esc(op.value??op.order?.map(id=>this.label(id)).join(' → ')??'Apply selected repair')}</p><button type="button" class="secondary" data-remove-repair="${esc(key)}">Remove ${esc(names[op.kind])}</button></li>`).join('') || '<li>No repairs queued. Select a figure, accept a text proposal, or queue a reading order.</li>';
        $('#repair-count').textContent=`${this.queue.items.size} / 25 repairs queued for this source.`;$('#repair-undo').disabled=!this.queue.history.length;$('#verify-transaction').disabled=!this.queue.items.size;
    }
    async export() {
        if(this.deps.isBusy() || !this.queue.items.size)return;const doc=this.doc,request=this.queue.request(),reviewPlan=doc.review.report();this.deps.setBusy(true);$('#verify-transaction').disabled=true;$('#repair-status').textContent='Applying queued repairs and verifying all pages…';
        try {
            const result=await(await this.deps.checkedFetch(`/api/documents/${doc.snapshot.id}/repair-transactions`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)})).json();
            if(doc!==this.doc)return;
            const entry={pdf:blob(result.pdf,'application/pdf'),filename:result.evidence.outputFilename,sourceType:doc.snapshot.sourceType,evidence:{...result.evidence,request,reviewPlan},previews:result.previews};
            entry.pdfUrl=this.deps.objectUrl(entry.pdf);entry.recordUrl=this.deps.jsonUrl(entry.evidence);entry.previews=entry.previews.map(p=>({pageNumber:p.pageNumber,before:this.deps.objectUrl(blob(p.before,'image/png')),after:this.deps.objectUrl(blob(p.after,'image/png'))}));
            this.exports.unshift(entry);this.renderExports();$('#repair-status').textContent=`Verified ${result.evidence.appliedActions.length} repairs across ${result.evidence.pagesVerified} page(s). Downloads are ready.`;
            $('#transaction-history').scrollIntoView({block:'start',behavior:'smooth'});$('#transaction-history a')?.focus({preventScroll:true});
        }catch(error){$('#repair-status').textContent=error.message;}
        finally{this.deps.setBusy(false);this.renderQueue();}
    }
    renderExports() {
        const history=$('#transaction-history');history.hidden=false;
        history.innerHTML='<h2>Verified repair exports</h2>'+this.exports.map((e,i)=>`<article class="repair-entry"><h3>${esc(e.filename)}</h3><p>${e.evidence.appliedActions.length} applied repairs · ${e.evidence.pagesVerified} pages verified</p><div class="repair-links"><a href="${e.pdfUrl}" download="${esc(e.filename)}">Download verified PDF</a><a href="${e.recordUrl}" download="${esc(e.filename.replace(/\.pdf$/i,'-record.json'))}">Download transaction record</a></div><details><summary>Before and after pages</summary>${e.previews.map(p=>`<div class="repair-comparison"><figure><figcaption>Before · Page ${p.pageNumber}</figcaption><img src="${p.before}" alt="Source page ${p.pageNumber}"></figure><figure><figcaption>After · Page ${p.pageNumber}</figcaption><img src="${p.after}" alt="Repaired page ${p.pageNumber}; preview pixels matched"></figure></div>`).join('')}</details><details><summary>Applied actions and verification</summary><pre>${esc(JSON.stringify(e.evidence,null,2))}</pre></details><button type="button" data-continue-transaction="${i}">Continue reviewing this export</button><p class="muted">Starts a new review session. The previous queue and review notes remain in the transaction record.</p></article>`).join('');
        history.querySelectorAll('[data-continue-transaction]').forEach(button=>button.addEventListener('click',async()=>{
            if(this.deps.isBusy())return;this.deps.setBusy(true);button.disabled=true;
            try{await this.deps.continueWith(this.exports[Number(button.dataset.continueTransaction)]);}catch(error){$('#repair-status').textContent=error.message;}finally{this.deps.setBusy(false);button.disabled=false;}
        }));
    }
    async benchmark(comparison) {
        $('#run-benchmark').disabled=true;$('#comparison-file').disabled=true;$('#benchmark-status').textContent='Running the synthetic tagging and repair benchmark…';
        try {
            const report=await(await this.deps.checkedFetch(`/api/benchmarks/${comparison?'compare':'run'}`,{method:'POST',headers:{'Content-Type':'application/json'},...(comparison?{body:JSON.stringify(comparison)}:{})})).json();
            const pct=n=>`${(n*100).toFixed(1)}%`,metric=(label,m)=>`<tr><th scope="row">${esc(label)}</th><td>${pct(m.precision)}</td><td>${pct(m.recall)}</td><td>${pct(m.coverage)}</td><td>${m.correct}/${m.total}</td></tr>`;
            $('#benchmark-results').innerHTML=`<p>${esc(report.limitation)}</p><table><caption>Tagging metrics · ${esc(report.corpusVersion)}</caption><thead><tr><th scope="col">Predictor</th><th scope="col">Precision</th><th scope="col">Recall</th><th scope="col">Coverage</th><th scope="col">Correct</th></tr></thead><tbody>${metric(report.algorithm,report.metrics)}${metric('Paragraph-only baseline',report.paragraphBaseline)}${report.comparison?metric(report.comparisonLabel,report.comparison):''}</tbody></table>
                <p>${report.metrics.abstained} abstentions · ${report.metrics.falseArtifactPredictions} incorrect artifact predictions. Combined synthetic repair: ${Object.values(report.repairChecks).every(Boolean)?'preservation checks passed':'check failed'}.</p>
                <details><summary>Per-role metrics</summary><table><caption>Role-specific results</caption><thead><tr><th scope="col">Role</th><th scope="col">Precision</th><th scope="col">Recall</th><th scope="col">False positives</th><th scope="col">Missed</th></tr></thead><tbody>${report.metrics.byRole.map(r=>`<tr><th scope="row">${esc(r.role)}</th><td>${pct(r.precision)}</td><td>${pct(r.recall)}</td><td>${r.falsePositive}</td><td>${r.falseNegative}</td></tr>`).join('')}</tbody></table></details>
                <h3>Error examples</h3><ul>${report.rows.filter(r=>r.expected!==r.predicted).map(r=>`<li>${esc(r.text)} — expected ${esc(r.expected)}, proposed ${esc(r.predicted)}</li>`).join('') || '<li>No errors in this small corpus.</li>'}</ul>
                <div class="repair-links"><a href="${this.deps.jsonUrl(report)}" download="benchmark-report.json">Download benchmark report</a><a href="${this.deps.jsonUrl(report.comparisonTemplate)}" download="comparison-template.json">Download comparison template</a></div>`;
            $('#benchmark-status').textContent=`Benchmark complete: ${report.metrics.correct} of ${report.metrics.total} expected roles matched.`;
        }catch(error){$('#benchmark-status').textContent=error.message;}
        finally{$('#run-benchmark').disabled=false;$('#comparison-file').disabled=false;}
    }
}
