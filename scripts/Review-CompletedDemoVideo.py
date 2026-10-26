"""Decode the complete local edit; check duration, captions and every shot."""
from pathlib import Path
import sys,json,re,subprocess,argparse
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tmp/video-tools'))
import imageio_ffmpeg
FFMPEG=imageio_ffmpeg.get_ffmpeg_exe()
parser=argparse.ArgumentParser()
parser.add_argument('--directory',type=Path,default=ROOT/'output/presentation-2026-10-25/completed-only')
args=parser.parse_args()
OUT=args.directory
data=json.loads((OUT/'timeline.json').read_text(encoding='utf-8'))
video=OUT/data['target'];qa=OUT/'qa';qa.mkdir(exist_ok=True)
meta=subprocess.run([FFMPEG,'-hide_banner','-i',str(video)],capture_output=True).stderr.decode('utf-8',errors='replace')
m=re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)',meta)
assert m,meta
duration=int(m[1])*3600+int(m[2])*60+float(m[3])
assert abs(duration-data['seconds'])<.15,duration
assert '1920x1080' in meta,meta
assert 'Audio:' not in meta
decoded=subprocess.run([FFMPEG,'-hide_banner','-loglevel','error','-i',str(video),'-f','null','-'],capture_output=True)
assert decoded.returncode==0,decoded.stderr.decode('utf-8',errors='replace')
frames=[]
for shot in data['shots']:
    point=(shot['start']+shot['end'])/2
    p=qa/f'{shot["index"]:02d}-{shot["key"]}.png'
    r=subprocess.run([FFMPEG,'-hide_banner','-loglevel','error','-y','-ss',str(point),'-i',str(video),'-frames:v','1',str(p)],capture_output=True)
    assert r.returncode==0 and p.exists(),shot
    frames.append(p)
for start in range(0,len(frames),6):
    contact=Image.new('RGB',(1920,1080),'#0e1725')
    for j,p in enumerate(frames[start:start+6]):
        im=Image.open(p);im.thumbnail((640,360))
        contact.paste(im,((j%3)*640,(j//3)*360))
    contact.save(qa/f'contact-{start//6+1}.png')
assert len(re.findall(r' --> ',(OUT/'CAPTIONS.srt').read_text(encoding='utf-8-sig')))==len(data['shots'])
proof=dict(passed=True,actualDurationSeconds=duration,dimensions=[1920,1080],fullDecodePassed=True,audio=False,shotsSampled=len(frames),newProviderCalls=0,excluded=data.get('excluded',data.get('removed',[])),bytes=video.stat().st_size)
(OUT/'verification.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(proof,ensure_ascii=False))
