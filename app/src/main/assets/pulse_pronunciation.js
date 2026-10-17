// Offline pronunciation prototype: curated entries are illustrative, not a complete lexicon.
(function(root){
"use strict";
const BM={
 "aku":["a","k","u"],"kita":["k","i","t","a"],"buku":["b","u","k","u"],
 "kaki":["k","a","k","i"],"cinta":["tʃ","i","n","t","a"],
 "sayang":["s","a","j","a","ŋ"],"malam":["m","a","l","a","m"],
 "pulang":["p","u","l","a","ŋ"],"hati":["h","a","t","i"],
 "kau":["k","au"],"pantai":["p","a","n","t","ai"]
};
const EN={
 "i":["AY1"],"you":["Y","UW1"],"love":["L","AH1","V"],
 "the":["DH","AH0"],"night":["N","AY1","T"],"light":["L","AY1","T"],
 "heart":["HH","AA1","R","T"],"music":["M","Y","UW1","Z","IH0","K"],
 "walking":["W","AO1","K","IH0","NG"],"down":["D","AW1","N"],
 "street":["S","T","R","IY1","T"],"shine":["SH","AY1","N"]
};
const BM_UNITS=["ng","ny","sy","kh","ai","au","oi","a","e","i","o","u","b","c","d","f","g","h","j","k","l","m","n","p","q","r","s","t","v","w","x","y","z"];
function bmFallback(word){
 const out=[];let i=0;
 while(i<word.length){
  const unit=BM_UNITS.find(x=>word.startsWith(x,i));
  if(!unit){i++;continue;}
  out.push(({ng:"ŋ",ny:"ɲ",sy:"ʃ",kh:"x",c:"tʃ",j:"dʒ",y:"j"}[unit]||unit));i+=unit.length;
 }
 return out;
}
function tokenize(word,language){
 const cleaned=String(word).normalize("NFKC").toLowerCase().replace(/[^a-z]/g,"");
 const lang=language==="bm"?"bm":"en";
 if(!cleaned)return {language:lang,phonemes:[],source:"empty"};
 const dict=lang==="bm"?BM:EN;
 if(dict[cleaned])return {language:lang,phonemes:dict[cleaned].slice(),source:"dictionary"};
 if(lang==="bm")return {language:lang,phonemes:bmFallback(cleaned),source:"heuristic"};
 // Unknown English spellings are deliberately not presented as accurate phonemes.
 return {language:lang,phonemes:[],source:"unknown",word:cleaned};
}
function pronounceScore(notes,language){
 return notes.map(note=>({...note,pronunciation:tokenize(note.word,language)}));
}
root.PulsePronunciation={tokenize,pronounceScore};
})(typeof globalThis!=="undefined"?globalThis:this);
