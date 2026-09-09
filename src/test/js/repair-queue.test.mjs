import {test} from 'node:test';
import assert from 'node:assert/strict';
import {RepairQueue,moveSibling} from '../../main/resources/static/repair-queue.mjs';
const op={kind:'ALT_TEXT',targetId:'s4',value:'Chart description',confirmed:true};
test('queue captures immutable accepted actions and source identity',()=>{
    const q=new RepairQueue('source-a');q.put(op);const request=q.request();q.put({...op,value:'Changed'});
    assert.equal(request.operations[0].value,'Chart description');assert.equal(request.originalSha256,'source-a');
    request.operations[0].value='External change';assert.equal(q.request().operations[0].value,'Changed');
    assert.equal(new RepairQueue('source-b').items.size,0);
});
test('remove, replacement and undo restore exact queue state',()=>{
    const q=new RepairQueue('source');q.put(op);q.put({...op,value:'Second'});q.remove('ALT_TEXT:s4');
    assert.equal(q.items.size,0);q.undo();assert.equal(q.request().operations[0].value,'Second');q.undo();
    assert.equal(q.request().operations[0].value,op.value);q.undo();assert.equal(q.items.size,0);assert.equal(q.undo(),false);
});
test('queue refuses unaccepted actions and limits transaction size',()=>{
    const q=new RepairQueue('source');assert.throws(()=>q.put({...op,confirmed:false}));
    for(let i=0;i<25;i++)q.put({...op,targetId:'s'+i});assert.throws(()=>q.put({...op,targetId:'extra'}));
    q.put({...op,targetId:'s1'});assert.equal(q.items.size,25);
});
test('sibling moves preserve the complete unique sequence',()=>{
    const original=['a','b','c'];assert.deepEqual(moveSibling(original,'b',-1),['b','a','c']);assert.deepEqual(original,['a','b','c']);
    assert.deepEqual(moveSibling(original,'a',-1),original);assert.throws(()=>moveSibling(['a','a'],'a',1));assert.throws(()=>moveSibling(original,'x',1));
});
