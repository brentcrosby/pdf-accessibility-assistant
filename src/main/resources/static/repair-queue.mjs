export class RepairQueue {
    constructor(sourceHash) {this.sourceHash=sourceHash;this.items=new Map();this.history=[];this.revision=0;}
    key(op) {return `${op.kind}:${op.targetId || 'document'}`;}
    checkpoint() {this.history.push(structuredClone([...this.items]));if(this.history.length>50)this.history.shift();this.revision++;}
    put(op) {
        if(!op.confirmed)throw new Error('Accept the repair before adding it to the queue.');
        if(this.items.size>=25 && !this.items.has(this.key(op)))throw new Error('A transaction can contain up to 25 repairs.');
        this.checkpoint();this.items.set(this.key(op),structuredClone(op));
    }
    remove(key) {if(this.items.has(key)){this.checkpoint();this.items.delete(key);}}
    undo() {const previous=this.history.pop();if(!previous)return false;this.items=new Map(previous);this.revision++;return true;}
    request() {return structuredClone({originalSha256:this.sourceHash,semanticVersion:'semantics-v1',operations:[...this.items.values()]});}
}
export function moveSibling(order,id,delta) {
    const copy=[...order],index=copy.indexOf(id);
    if(new Set(copy).size!==copy.length || index<0 || ![-1,1].includes(delta))throw new Error('Invalid sibling sequence.');
    const to=index+delta;if(to<0 || to>=copy.length)return copy;
    [copy[index],copy[to]]=[copy[to],copy[index]];return copy;
}
