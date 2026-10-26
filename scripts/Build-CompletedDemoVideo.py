"""Assemble existing completed UI recordings and original experiment captures.

No browser/server interaction, keys or new provider calls. Silent review edit.
"""
from pathlib import Path
from io import BytesIO
from hashlib import sha256
import argparse,json,subprocess,sys
from PIL import Image,ImageDraw,ImageFont
from pypdf import PdfReader

ROOT=Path(__file__).resolve().parents[1]
REH=ROOT/'output/presentation-2026-10-25/rehearsal'
OUT=ROOT/'output/presentation-2026-10-25/completed-only'
FFMPEG=Path.home()/'AppData/Local/ms-playwright/ffmpeg-1011/ffmpeg-win64.exe'
FONT='C:/Windows/Fonts/malgun.ttf'
BOLD='C:/Windows/Fonts/malgunbd.ttf'
W,H=1920,1080

def main():
    global FFMPEG
    parser=argparse.ArgumentParser();parser.add_argument('--source',required=True)
    parser.add_argument('--from-part',type=int,default=1,help='Reuse earlier already-rendered parts when repairing a later shot.')
    args=parser.parse_args()
    # Full local encoder is installed only in the ignored task tool directory.
    tool_dir=ROOT/'tmp/video-tools'
    if tool_dir.exists():
        sys.path.insert(0,str(tool_dir))
        import imageio_ffmpeg
        FFMPEG=Path(imageio_ffmpeg.get_ffmpeg_exe())
    else:
        raise RuntimeError('Full FFmpeg required; use the task-local imageio-ffmpeg runtime.')
    OUT.mkdir(parents=True,exist_ok=True)
    assets=OUT/'assets';parts=OUT/'parts'
    assets.mkdir(exist_ok=True);parts.mkdir(exist_ok=True)
    report=PdfReader(args.source)
    def run(arguments):
        result=subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-y',*arguments],capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8',errors='replace'))
        return result
    def frame(clip,at,name):
        path=assets/(name+'-source.png')
        run(['-ss',str(at),'-i',str(REH/'clips'/clip),'-frames:v','1',str(path)])
        return Image.open(path).convert('RGB')
    def evidence(page,name):
        found=[im for im in report.pages[page-1].images if im.name.split('.')[0]==name]
        assert len(found)==1,(page,name)
        return found[0].image.convert('RGB')
    def wrapped(draw,s,font,width):
        lines=[]
        for paragraph in s.split('\n'):
            line=''
            for ch in paragraph:
                if draw.textlength(line+ch,font=font)>width and line:
                    lines.append(line);line=ch
                else:line+=ch
            lines.append(line)
        return lines
    def still(name,title,img,notes,mode,focus=None):
        dest=assets/(name+'.png')
        im=Image.new('RGB',(W,H),'#0e1725');d=ImageDraw.Draw(im)
        d.text((40,25),title,font=ImageFont.truetype(BOLD,39),fill='#e8eff7')
        # UI stills may omit the old rehearsal caption below the application.
        if focus:img=img.crop(focus)
        original=img.copy();img.thumbnail((1190,850),Image.Resampling.LANCZOS)
        im.paste(img,(40+(1190-img.width)//2,117+(850-img.height)//2))
        d.rounded_rectangle((1260,116,1880,967),radius=20,fill='#162438',outline='#2b405b',width=2)
        y=145
        for heading,body in notes:
            d.text((1284,y),heading,font=ImageFont.truetype(BOLD,28),fill='#6bd6e8');y+=49
            font=ImageFont.truetype(FONT,27)
            for line in wrapped(d,body,font,566):
                d.text((1284,y),line,font=font,fill='#e8eff7');y+=40
            y+=29
        assert y<970,(name,y)
        d.text((42,1000),mode,font=ImageFont.truetype(BOLD,24),fill='#70dfb2')
        d.text((42,1038),'기존 화면·기록 재생 | 읽기용 정지·저속 구간 포함 | 무음 검토본',font=ImageFont.truetype(FONT,20),fill='#adbed2')
        im.save(dest)
        return dest
    shots=[]
    def add_still(seconds,key,title,image,notes,mode,focus=None):
        path=still(key,title,image,notes,mode,focus)
        shots.append(dict(kind='still',seconds=seconds,key=key,title=title,source=str(path),mode=mode))
    def add_clip(seconds,key,title,filename,speed):
        shots.append(dict(kind='clip',seconds=seconds,key=key,title=title,source=str(REH/'clips'/filename),speed=speed,mode='기존 실제 화면 녹화 · 모의 공급자 · 저속 재생'))
    user=frame('02_USER_INTAKE.webm',3,'user')
    guide=frame('02_USER_INTAKE.webm',10,'guide')
    search=frame('03_PUBLIC_SEARCH.webm',6,'search')
    lawyer=frame('04_LAWYER_REVIEW.webm',6,'lawyer')
    conflict=frame('05_ERRORS_SECURITY_COST.webm',3.2,'conflict')
    ui_focus=(0,0,1920,950)
    add_clip(22,'01_user','USER 사건 입력·접수','02_USER_INTAKE.webm',2)
    add_still(18,'02_form','선택형 입력으로 사건을 구조화합니다',user,[('입력','사건 유형·문의 목적·당사자 위치·자료를 선택합니다.'),('확인','짧은 사건 설명을 접수 정보로 정리합니다.'),('구현 범위','초반 안내는 규칙·템플릿 기반입니다.')],'더미 사례 · 실제 앱 화면 · 정지 구간',ui_focus)
    add_still(20,'03_guide','안내와 준비 자료를 확인합니다',guide,[('확인','접수된 문의 내용·준비 자료·다음 단계를 확인합니다.'),('ID 연결','같은 문의에서 검색 요청으로 이어집니다.'),('다음 행동','준비할 자료를 확인한 뒤 검색 조건을 살펴봅니다.')],'더미 사례 · 초기 안내 LLM 미호출',ui_focus)
    add_clip(16,'04_search','저장 조건으로 공개 정보를 탐색합니다','03_PUBLIC_SEARCH.webm',2.1)
    add_still(14,'05_search_result','검색 요청과 결과를 같은 ID로 확인합니다',search,[('흐름','조건 확인 → 요청 생성 → 결과 저장·조회'),('출처','변협 도메인 대상 + 일반 웹 검색 구조'),('현재 장면','모의 검색 결과입니다. 실제 검색 품질 검증과 구분합니다.')],'검색 모의 공급자 · 기록 재생 · 새 검색 호출 없음',ui_focus)
    add_clip(16,'06_lawyer','변호사 목록·상세·상담 준비','04_LAWYER_REVIEW.webm',2.3)
    add_still(24,'07_lawyer_detail','상태와 내부 메모로 상담을 준비합니다',lawyer,[('업무','배정 문의의 상세를 확인하고 상태를 관리합니다.'),('메모','검토 사항과 후속 업무를 기록합니다.'),('연결','문의 번호를 유지하며 접수부터 검토까지 이어집니다.')],'더미 사례 · 실제 LAWYER 화면 · 정지 구간',ui_focus)
    for key,page,name,title,reason in [
        ('A4',9,'Im75','세션 예산 부족','SESSION_BUDGET_EXCEEDED'),
        ('A5',10,'Im82','일일 예산 부족','DAILY_BUDGET_EXCEEDED'),
        ('A6',11,'Im89','월 예산 부족','MONTHLY_BUDGET_EXCEEDED')]:
        add_still(18,key,key+' · '+title,evidence(page,name),[('실험 조건','해당 예산을 $0.0003으로 낮춘 모의 실험입니다.'),('실제 기록',reason+'\n장부 증가 0'),('해석','다음 유료 단계 전에 예산 부족으로 중단합니다. 캡처는 input_guardrail 차단 기록입니다.')],'모의 실험 원본 · OpenAI 과금 실측 아님')
    add_still(20,'V02','V02 · 실제 OpenAI 단독 생성',evidence(5,'Im36'),[('실제 기록','입력 14 / 출력 1토큰'),('계산 비용','$0.0000027'),('의미','키 인증을 넘어 실제 생성 응답을 받은 기록입니다. 앱 전체 경로 검증과는 별개입니다.')],'실제 OpenAI 과거 기록 재생 · 새 유료 호출 없음')
    add_still(10,'V02_usage','OpenAI 사용량 대조 · 첫 요청',evidence(5,'Im39'),[('공급자 화면','누적 요청 1회\n입력 14토큰'),('대조','V02 응답 usage와 맞춰 확인합니다.'),('주의','Spend $0.00은 반올림 표시입니다.')],'실제 OpenAI 사용량 과거 캡처 · 조직 전체 October spend와 구분')
    add_still(22,'V03','V02~V03 · 앱 경로 생성과 정산',evidence(5,'Im41'),[('실제 기록','입력 59 / 출력 64토큰\ncostSource=REPORTED\n예약 종료 reserved=0'),('계산 비용','$0.00004725'),('공급자 누적','요청 1 → 2\n입력 14 → 73 (+59)')],'실제 LLM · 모의 Guard · 해당 앱 usage는 원문 표 기록 기준')
    add_still(22,'C1','C1-16 · 출력 상한은 품질도 제한합니다',evidence(16,'Im127'),[('실험 조건','출력 상한 16토큰'),('실제 기록','입력 59 / 출력 16토큰\n답변이 문장 중간에서 끊겼습니다.'),('계산 비용','$0.00001845\n더 작은 상한이 항상 좋은 안내는 아닙니다.')],'실제 LLM · 모의 Guard · C1-300은 미검증으로 제외')
    add_still(12,'C1_usage','OpenAI 사용량 대조 · 확인된 3회',evidence(5,'Im43'),[('공급자 누적','요청 3회 / 입력 132토큰'),('응답·장부 합계','입력132 · 출력81토큰\n토큰 기준 계산 $0.0000684'),('구분','모의 Guard 장부는 실제 OpenAI 청구에 더하지 않습니다.')],'실제 사용량 화면 · 비용은 원문 단가 기반 계산액')
    add_still(20,'B2_cost','B2 · usage가 없으면 추정 비용으로 기록',evidence(14,'Im113'),[('실험 조건','timeout 1ms'),('실제 기록','답변 estimated $0.0000834\n장부 $0.00008535\nreserved=0'),('해석','실제 사용량을 모르면 비용을 0으로 단정하지 않고 추정 정산합니다.')],'실제 LLM 설정·미도달 관측 · Guard 모의 · 과거 기록')
    add_still(10,'cost_reconcile','비용 확인 · 사용량·정산·예약을 구분합니다',evidence(16,'Im127'),[('정상 경로','보고된 토큰으로 정산합니다.'),('예외 경로','usage 미확인은 추정 비용으로 분리합니다.'),('종료 확인','예약 잔여와 공급자 사용량을 함께 확인합니다.')],'기존 실험 기록 재확인 · 새 청구 없음')
    add_still(20,'A1','A1 · 101자 입력은 요청 검증에서 멈춥니다',evidence(7,'Im57'),[('입력','100자 한도를 넘는 101자'),('실제 기록','HTTP 400\n장부 증가 0'),('다음 행동','입력을 줄여 다시 확인합니다. 이 캡처에서 세부 오류 코드 필드는 미확인입니다.')],'모의 실험 원본 · 입력 오류 처리 · 보안 시연 제외')
    add_still(30,'409','409 · 오래된 화면의 저장을 중단합니다',conflict,[('발생','다른 탭이 문의를 먼저 수정했습니다.'),('실제 화면','충돌 안내와 작성한 메모가 남습니다. 자동 재저장을 하지 않습니다.'),('다음 행동','저장 결과 조회 → 확인 후 편집 계속 → 명시적으로 저장합니다.')],'기존 앱 409 화면 · 더미 사례 · 정지 구간',ui_focus)
    add_still(20,'B2_error','B2 · 외부 오류는 안전 응답으로 종료',evidence(14,'Im113'),[('실제 기록','fallback=true\nLLM_UNAVAILABLE\nmodel=unknown'),('공급자 대조','보고서에서 OpenAI 요청·입력 누적은 증가하지 않았습니다.'),('의미','실패 이유와 정산을 남깁니다. 모든 timeout이 무료라는 뜻은 아닙니다.')],'실제 LLM 설정·미도달 관측 · 기존 실험 캡처')
    mobile=Image.open(REH/'mobile-rehearsal.png').convert('RGB')
    # Enlarge the existing phone region rather than shrinking the whole old title card.
    add_still(20,'mobile','모바일 맛보기 · 같은 접수 흐름',mobile,[('화면','390px 브라우저에서 접수 폼을 확인한 장면입니다.'),('이번 영상','기존 검수 화면의 정지 미리보기입니다.'),('구분','실제 휴대폰 접속·터치 동작 녹화는 별도입니다.')],'390px 브라우저 기존 캡처 · 모바일 실제 조작 녹화 아님',(1200,105,1615,977))
    assert sum(s['seconds'] for s in shots)==390
    timeline=[];elapsed=0
    for i,s in enumerate(shots,1):
        part=parts/f'{i:02d}.mp4'
        if s['kind']=='still':
            arguments=['-loop','1','-framerate','10','-i',s['source'],'-t',str(s['seconds'])]
            vf='format=yuv420p'
        else:
            arguments=['-itsscale',str(s['speed']),'-i',s['source'],'-t',str(s['seconds'])]
            vf='format=yuv420p'
        if i < args.from_part:
            assert part.exists(),part
        else:
            run([*arguments,'-vf',vf,'-r','10','-an','-c:v','libx264','-preset','veryfast','-crf','20','-threads','4','-movflags','+faststart',str(part)])
        timeline.append(dict(index=i,start=elapsed,end=elapsed+s['seconds'],**s));elapsed+=s['seconds']
        print(f'{i:02d}/{len(shots)} {s["key"]} {s["seconds"]}s',flush=True)
    listing=parts/'concat.txt'
    listing.write_text('\n'.join("file '"+f'{i:02d}.mp4'+"'" for i in range(1,len(shots)+1)),encoding='utf-8')
    target=OUT/'COMPLETED_FLOW_COST_ERROR_MOBILE_6M30_SILENT.mp4'
    run(['-f','concat','-safe','0','-i',str(listing),'-c','copy','-movflags','+faststart',str(target)])
    def stamp(s):return f'{s//3600:02d}:{s%3600//60:02d}:{s%60:02d},000'
    captions=[]
    for i,s in enumerate(timeline,1):captions.append(f'{i}\n{stamp(s["start"])} --> {stamp(s["end"])}\n{s["title"]}\n{s["mode"]}\n')
    (OUT/'CAPTIONS.srt').write_text('\n'.join(captions),encoding='utf-8-sig')
    manifest=dict(target=target.name,seconds=390,silent=True,width=W,height=H,sourceReportSha256=sha256(Path(args.source).read_bytes()).hexdigest(),newProviderCalls=0,excluded=['Fixed/Dynamic/security demo','admin dashboards 09-13','A7 unmerged branch','B1','C1-300','live mobile device','live SSE provider tests'],shots=timeline)
    (OUT/'timeline.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print('DONE '+str(target),flush=True)

if __name__=='__main__':main()
