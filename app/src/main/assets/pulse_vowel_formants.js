// Deterministic offline vowel-formant prototype. No network, model, or external assets.
(function(root) {
"use strict";
const RATE=22050;
const FORMANTS={
 a:[800,1200,2800],e:[500,1900,2600],i:[300,2300,3000],
 o:[500,900,2600],u:[350,800,2400]
};
function frequency(midi) {
 if(!Number.isFinite(midi)||midi<24||midi>96)throw Error("MIDI note out of range");
 return 440*Math.pow(2,(midi-69)/12);
}
function renderVowel(vowel,midi,duration) {
 if(!FORMANTS[vowel])throw Error("Unsupported vowel");
 if(!Number.isFinite(duration)||duration<=0||duration>8)throw Error("Invalid duration");
 const f0=frequency(midi),length=Math.floor(duration*RATE),out=new Float32Array(length);
 const formants=FORMANTS[vowel],widths=[115,180,260],partials=Math.min(80,Math.floor(9000/f0));
 const harmonics=[];
 for(let h=1;h<=partials;h++) {
  const hz=h*f0;
  let gain=0;
  for(let k=0;k<3;k++){
   const z=(hz-formants[k])/widths[k];
   gain+=([1,.7,.45][k])*Math.exp(-.5*z*z);
  }
  harmonics.push(gain/Math.pow(h,.55));
 }
 const norm=harmonics.reduce((sum,v)=>sum+Math.abs(v),0)||1;
 const attack=Math.min(.04,duration*.18),release=Math.min(.09,duration*.23);
 for(let n=0;n<length;n++){
  const t=n/RATE;
  const envelope=Math.min(1,t/Math.max(attack,1/RATE),Math.max(0,(duration-t)/Math.max(release,1/RATE)));
  let sample=0;
  for(let h=1;h<=partials;h++)sample+=harmonics[h-1]*Math.sin(2*Math.PI*f0*h*t);
  out[n]=sample*envelope/norm;
 }
 let peak=0;for(const s of out)peak=Math.max(peak,Math.abs(s));
 if(peak>0){const scale=.85/peak;for(let n=0;n<length;n++)out[n]*=scale;}
 return out;
}
root.PulseVowelFormants={RATE,FORMANTS,frequency,renderVowel};
})(typeof globalThis!=="undefined"?globalThis:this);
