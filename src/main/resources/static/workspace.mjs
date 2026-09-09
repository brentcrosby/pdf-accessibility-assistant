const $ = selector => document.querySelector(selector);
const escape = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const tasks = [
    ['overview','Overview','Start with the suggested next steps.'],
    ['content','Page content','Select something on the page to inspect it.'],
    ['tags','Tags & alt text','Explore existing tags and describe meaningful figures.'],
    ['suggestions','Suggested tags','Review text roles before adding them to your PDF.'],
    ['order','Reading order','Arrange tagged content in a meaningful sequence.'],
    ['export','Review & export','Check your changes, then create a verified copy.']
];

/** Keep one viewer in place while switching tools; existing controls retain their state. */
export class Workspace {
    constructor() {
        this.mode='overview';this.scrolls=new Map();
        $('#workflow-nav').innerHTML='<span class="eyebrow">WORKSPACE</span>'+tasks.map(([id,label],i)=>`<button type="button" data-workflow="${id}" aria-controls="tool-dock"><span class="task-number">0${i+1}</span><span>${label}</span>${id==='export'?'<span id="nav-repair-count" class="count-badge">0</span>':''}</button>`).join('');
        const dock=document.createElement('aside');dock.id='tool-dock';dock.setAttribute('aria-label','Active review tool');
        dock.innerHTML='<div class="tool-heading"><span id="tool-step" class="eyebrow"></span><h2 id="tool-title" tabindex="-1"></h2><p id="tool-description"></p></div><div id="tool-content"></div><div class="tool-feedback" id="tool-feedback"></div>';
        $('.workspace').append(dock);
        const overview=document.createElement('section');overview.id='overview-panel';overview.dataset.workspaceViews='overview';overview.innerHTML='<h3>Your next steps</h3><div id="next-steps"><p>Reading the document structure…</p></div>';
        $('#tool-content').append(overview);
        const move=(element,views)=>{element.dataset.workspaceViews=views;$('#tool-content').append(element);};
        move($('.review-panel'),'content');
        move($('#semantic-heading').parentElement,'overview tags suggestions order');
        const exportPane=document.createElement('section');exportPane.id='export-pane';exportPane.dataset.workspaceViews='export';$('#tool-content').append(exportPane);
        exportPane.append($('#repair-heading').parentElement);
        const notes=document.createElement('details');notes.innerHTML='<summary>Review notes and reports</summary>';
        notes.append($('#queue-heading').closest('section'),$('#report-controls'),$('#report-details'));exportPane.append(notes);
        const advanced=document.createElement('details');advanced.innerHTML='<summary>Metadata-only export</summary>';advanced.append($('#export').closest('section'));exportPane.append(advanced);
        const findings=$('.findings-panel');move(findings,'overview');
        const identity=$('#source-identity').closest('details');move(identity,'overview');
        const artifactPane=document.createElement('div');artifactPane.dataset.workspaceViews='content';artifactPane.append($('#artifact-panel'));$('#tool-content').append(artifactPane);
        // History uses its own hidden flag until an export exists, so gate its parent instead.
        const history=document.createElement('div');history.dataset.workspaceViews='export';history.append($('#transaction-history'),$('#artifact-history'),$('#export-history'));$('#tool-content').append(history);
        $('#tool-feedback').append($('#repair-status'));
        $('#benchmark-body').append($('#benchmark-heading').closest('section'));
        $('#open-benchmark').addEventListener('click',()=>$('#benchmark-dialog').showModal());
        $('#benchmark-dialog').addEventListener('close',()=>$('#open-benchmark').focus());
        $('#open-document').addEventListener('click',()=>{
            const card=$('.upload-card');card.hidden=!card.hidden;$('#open-document').setAttribute('aria-expanded',String(!card.hidden));if(!card.hidden)$('#file').focus();
        });
        $('#review-changes').addEventListener('click',()=>this.show('export',true));
        document.addEventListener('click',event=>{
            const task=event.target.closest('[data-workflow]');if(task)this.show(task.dataset.workflow,true);
        });
        $('#workflow-nav').addEventListener('keydown',event=>{
            if(!['ArrowDown','ArrowUp','Home','End'].includes(event.key))return;
            const buttons=[...$('#workflow-nav').querySelectorAll('button')],index=buttons.indexOf(document.activeElement);if(index<0)return;
            event.preventDefault();const next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length;buttons[next].focus();
        });
        this.refresh();
    }
    open() {
        document.body.classList.add('document-open');$('#welcome').hidden=true;$('.upload-card').hidden=true;$('#open-document').hidden=false;$('#open-document').setAttribute('aria-expanded','false');
        this.scrolls.clear();$('#next-steps').innerHTML='<p>Reading the document structure…</p>';this.show('overview');
    }
    show(mode,focus=false) {
        if(!tasks.some(t=>t[0]===mode))return;
        this.scrolls.set(this.mode,$('#tool-content').scrollTop);this.mode=mode;this.refresh();$('#tool-content').scrollTop=this.scrolls.get(mode)||0;
        if(focus){$('#tool-title').focus({preventScroll:true});if(window.innerWidth<761)$('#tool-dock').scrollIntoView({block:'start'});}
    }
    refresh() {
        const index=tasks.findIndex(t=>t[0]===this.mode),[,title,description]=tasks[index];
        document.body.dataset.workflow=this.mode;$('#tool-title').textContent=title;$('#tool-description').textContent=description;$('#tool-step').textContent=`TOOL ${index+1} OF ${tasks.length}`;
        document.querySelectorAll('[data-workspace-views]').forEach(el=>el.hidden=!el.dataset.workspaceViews.split(' ').includes(this.mode));
        $('#workflow-nav').querySelectorAll('[data-workflow]').forEach(button=>{if(button.dataset.workflow===this.mode)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
    }
    update(data,queue) {
        const count=queue?.items.size||0;$('#header-repair-count').textContent=count;$('#nav-repair-count').textContent=count;
        if(!data)return;
        const figures=data.structure.nodes.filter(n=>n.altEditable&&!n.altText?.trim()),remaining=figures.filter(n=>!queue.items.has('ALT_TEXT:'+n.id)).length;
        const tags=data.proposals.filter(p=>!queue.items.has('TAG_TEXT:'+p.id)).length;
        const unresolved=data.structure.regions.filter(m=>m.status==='UNRESOLVED').length;
        const card=(mode,label,detail,badge)=>`<button type="button" class="next-step" data-workflow="${mode}"><span><strong>${escape(label)}</strong><small>${escape(detail)}</small></span><span class="count-badge">${badge}</span><span aria-hidden="true">→</span></button>`;
        $('#next-steps').innerHTML=card('tags','Describe figures',remaining?'Figures ready for a description':'No mapped figures awaiting alt text',remaining)
            +card('suggestions','Review suggested tags','Choose roles for untagged text',tags)
            +card('order','Check reading order','Verify the sequence by looking at the page','↕')
            +(unresolved?card('content','Inspect unresolved content','Some content could not be mapped',unresolved):'')
            +'<p class="muted">Counts describe supported observations in this source. Queued repairs are applied when you export.</p>';
        this.refresh();
    }
    unavailable() {
        $('#next-steps').innerHTML='<p>Semantic review is unavailable for this document. You can still inspect its pages and use the available document findings.</p><button type="button" class="secondary" data-workflow="content">Inspect page content</button>';
    }
}
