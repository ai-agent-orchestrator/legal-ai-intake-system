const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),dir=path.join(root,'output/presentation-2026-10-25/rehearsal');
const data=JSON.parse(fs.readFileSync(path.join(dir,'rehearsal.json'),'utf8'));if(!data.complete)throw new Error('A complete rehearsal is required');
const media=JSON.parse(fs.readFileSync(path.join(dir,'media-qc.json'),'utf8'));
const ffmpeg=path.join(process.env.LOCALAPPDATA,'ms-playwright/ffmpeg-1011/ffmpeg-win64.exe');
const master=path.join(dir,'PRESENTATION_REHEARSAL_SILENT.webm');
const cut=prefix=>{const c=data.chapters.find(c=>c.title.startsWith(prefix));if(!c)throw new Error('Missing chapter '+prefix);return c.seconds;};
const specs=[['01_OPENING',0,cut('01 ')],['02_USER_INTAKE',cut('01 '),cut('04 ')],['03_PUBLIC_SEARCH',cut('04 '),cut('05 ')],['04_LAWYER_REVIEW',cut('05 '),cut('06 ')],['05_ERRORS_SECURITY_COST',cut('06 '),cut('07 ')],['06_ADMIN_MOBILE',cut('07 '),media.duration]];
const out=path.join(dir,'clips');fs.mkdirSync(out,{recursive:true});const results=[];
for(const [name,start,end]of specs){const file=path.join(out,name+'.webm');execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-y','-ss',start.toFixed(3),'-i',master,'-t',(end-start).toFixed(3),'-an','-c:v','libvpx','-b:v','2000k','-deadline','realtime','-cpu-used','8','-threads','4',file],{windowsHide:true,timeout:120000,stdio:'pipe'});results.push({file:path.basename(file),start,end,duration:end-start,bytes:fs.statSync(file).size});console.log(JSON.stringify({cut:name,seconds:Number((end-start).toFixed(2))}));}
const stamp=t=>{const ms=Math.round(t*1000);return`${String(Math.floor(ms/3600000)).padStart(2,'0')}:${String(Math.floor(ms/60000)%60).padStart(2,'0')}:${String(Math.floor(ms/1000)%60).padStart(2,'0')},${String(ms%1000).padStart(3,'0')}`;};
const captions=data.chapters.map((c,i)=>`${i+1}\n${stamp(c.seconds)} --> ${stamp(data.chapters[i+1]?.seconds||media.duration)}\n${c.title}\n${c.subtitle}\n`).join('\n');
fs.writeFileSync(path.join(dir,'REHEARSAL_CAPTIONS_TIMING_DRAFT.srt'),'\uFEFF'+captions);fs.writeFileSync(path.join(dir,'cuts.json'),JSON.stringify({master:'PRESENTATION_REHEARSAL_SILENT.webm',format:'WebM VP8',silent:true,width:media.width,height:media.height,totalDuration:media.duration,cuts:results,warning:'Timing draft follows automated recording actions; align with recorded narration before final export.'},null,2));
console.log(JSON.stringify({cuts:results.length,totalSeconds:media.duration,silent:true,realProviderCalls:0}));
