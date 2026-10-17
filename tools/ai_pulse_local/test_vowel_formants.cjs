const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const src=fs.readFileSync(__dirname+'/../../app/src/main/assets/pulse_vowel_formants.js','utf8');
const ctx=vm.createContext({Float32Array,Math,Number,Error});vm.runInContext(src,ctx);
const {renderVowel,frequency,RATE}=ctx.PulseVowelFormants;
assert.ok(Math.abs(frequency(69)-440)<1e-10);
for(const vowel of ['a','e','i','o','u']){
 const a=renderVowel(vowel,69,.5),b=renderVowel(vowel,69,.5);
 assert.equal(a.length,Math.floor(.5*RATE));
 assert.deepEqual(Array.from(a),Array.from(b));
 const peak=Math.max(...a.map(Math.abs));
 assert.ok(peak<=.851&&peak>.5,'normalized audible nonclipping output');
 assert.ok(Math.abs(a[0])<1e-8,'smooth start');
 assert.ok(Math.abs(a[a.length-1])<.01,'smooth end');
}
assert.throws(()=>renderVowel('z',69,.5));
assert.throws(()=>renderVowel('a',69,100));
console.log('PASS: deterministic formant vowels, MIDI A4, envelope, bounds and clipping');
