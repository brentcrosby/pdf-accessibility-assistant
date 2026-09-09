import test from 'node:test';
import assert from 'node:assert/strict';
import {ReviewSession, filterRegions} from '../../main/resources/static/review-state.mjs';
const region = (id, kind, pageNumber = 1) => ({id, kind, pageNumber, text: `Sample ${id}`, bounds:{x:.1,y:.2,width:.3,height:.1}, unmappedGlyphs:0});
const page = (pageNumber, regions) => ({pageNumber,regions,rotation:0,width:600,height:800,geometryVersion:'page-crop-v2',warnings:['Not evaluated'],truncated:false});
function session(id = 'a') { const s = new ReviewSession({id,originalFilename:`${id}.pdf`,originalSha256:'abc',analysis:{pageCount:3}}); s.register(page(1,[region('t','TEXT'),region('p','PATH')])); return s; }

test('bulk updates deduplicate IDs and undo restores previous decisions and notes atomically', () => {
    const s = session(); s.apply(['t'],'KEEP_AS_CONTENT','Important');
    assert.equal(s.apply(['t','p','p'],'ARTIFACT_CANDIDATE'),2);
    assert.equal(s.decisions.get('t').note,'Important');
    s.undo(); assert.equal(s.decisions.get('t').decision,'KEEP_AS_CONTENT'); assert.equal(s.decisions.has('p'),false);
    s.remove('t'); assert.equal(s.decisions.size,0); s.undo(); assert.equal(s.decisions.get('t').note,'Important');
});
test('unknown regions or invalid decisions cannot partially mutate the queue', () => {
    const s = session();
    assert.throws(() => s.apply(['t','unknown'],'DEFER')); assert.equal(s.decisions.size,0);
    assert.throws(() => s.apply(['t'],'DELETE')); assert.throws(() => s.apply(['t'],'toString'));
    assert.throws(() => s.apply(['t'],'DEFER','x'.repeat(1001))); assert.equal(s.history.length,0);
});
test('filters combine page content type, search and review status without touching other regions', () => {
    const s = session(); s.apply(['p'],'ARTIFACT_CANDIDATE');
    const r = s.pages.get(1).regions;
    assert.deepEqual(filterRegions(r,{kind:'PATH',search:' P',status:'ARTIFACT_CANDIDATE'},s.decisions).map(r=>r.id),['p']);
    assert.deepEqual(filterRegions(r,{status:'UNREVIEWED'},s.decisions).map(r=>r.id),['t']);
    assert.deepEqual(filterRegions(r,{status:'UNMAPPED'},s.decisions),[]);
});
test('reports preserve source geometry and immutable evidence across edits and documents', () => {
    const a = session(), b = session('b'); a.apply(['t'],'DEFER','Review encoding');
    const report = a.report(); a.apply(['t'],'KEEP_AS_CONTENT','Updated');
    assert.equal(report.reviewerDecisions[0].decision,'DEFER');
    assert.equal(report.reviewerDecisions[0].region.bounds.y,.2);
    assert.equal(report.appliedToPdf,false); assert.equal(report.pagesInspected.length,1); assert.equal(report.pageCount,3);
    assert.equal(b.report().reviewerDecisions.length,0);
    report.reviewerDecisions[0].region.bounds.y = 9; assert.equal(a.region('t').bounds.y,.2);
});
test('empty reports and page navigation preserve decisions without suggesting all pages were inspected', () => {
    const s = session(); assert.equal(s.report().reviewerDecisions.length,0);
    s.apply(['p'],'ARTIFACT_CANDIDATE'); s.register(page(2,[region('i','IMAGE',2)])); s.apply(['i'],'DEFER');
    assert.equal(s.report().pagesInspected.length,2); assert.equal(s.decisions.size,2);
    s.undo(); assert.equal(s.decisions.has('p'),true); assert.equal(s.decisions.has('i'),false);
});
