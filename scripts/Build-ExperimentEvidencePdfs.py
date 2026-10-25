"""Match experiment intent, observed result, unmodified capture and cost evidence.

Input captures come from the user-supplied report; no server or paid calls.
"""
import argparse,json
from pathlib import Path
from io import BytesIO
from hashlib import sha256
from xml.sax.saxutils import escape
from pypdf import PdfReader
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.lib.utils import ImageReader

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/presentation-pdfs'
QA=ROOT/'tmp/pdfs/experiment-evidence'
TITLES={'COST_CONTROL':'Cost Control','ERROR_STOP_FLOW':'Error Stop Flow','DYNAMIC_GUARD':'Dynamic Guard'}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',required=True)
    args=parser.parse_args()
    source=Path(args.source)
    report=PdfReader(source)
    data=json.loads((OUT/'EXPERIMENT_RESULT_MAPPING.json').read_text(encoding='utf-8'))
    QA.mkdir(parents=True,exist_ok=True)
    pdfmetrics.registerFont(TTFont('K','C:/Windows/Fonts/malgun.ttf'))
    pdfmetrics.registerFont(TTFont('KB','C:/Windows/Fonts/malgunbd.ttf'))
    manifest=[]
    def capture(record):
        found=[im for im in report.pages[record['page']-1].images if im.name.split('.')[0]==record['image']]
        assert len(found)==1,record
        img=found[0].image.convert('RGB')
        manifest.append({'capture':record['capture'],'sourcePage':record['page'],'image':record['image'],'pixelsSha256':sha256(img.tobytes()).hexdigest()})
        return img
    for guide,title in TITLES.items():
        entries=[e for e in data['experiments'] if e['guide']==guide]
        count=1+sum(1+bool(e.get('provider')) for e in entries)
        path=OUT/(guide+'_DARK_TABLET.pdf')
        c=canvas.Canvas(str(path),pagesize=(1024,768)); c.setTitle(title+' | 실험·결과·비용 증거')
        number=0
        def text(s,x,y,w,size=16,color='#e8eff7',bold=False):
            p=Paragraph(escape(s).replace('\n','<br/>'),ParagraphStyle('p',fontName='KB' if bold else 'K',fontSize=size,leading=size*1.45,textColor=HexColor(color),wordWrap='CJK'))
            _,h=p.wrap(w,1400)
            assert y+h<713,(guide,s,y,h)
            p.drawOn(c,x,768-y-h);return h
        def base(heading,sub):
            nonlocal number
            number+=1
            c.setFillColor(HexColor('#0e1725'));c.rect(0,0,1024,768,fill=1,stroke=0)
            text(title.upper()+' / EXPERIMENT EVIDENCE',38,22,950,12,'#6bd6e8',True)
            text(heading,38,59,948,27,bold=True)
            text(sub,40,109,940,14,'#adbed2')
            c.setFillColor(HexColor('#adbed2'));c.setFont('K',10)
            c.drawString(40,25,'2026-10-25 | 원본 보고서 기반 재편집 | 새 유료 호출 없음')
            c.drawRightString(984,25,f'{number:02d} / {count:02d}')
        base('실험 제목 → 확인 의미 → 결과 → 증거 → 비용','각 항목의 상세 페이지로 이어집니다. 모의 장부와 실제 OpenAI 비용은 구분합니다.')
        y=158
        for e in entries:
            text(e['id']+' · '+e['title'],44,y,930,17,'#6bd6e8',True)
            c.linkRect('실험 결과·증거로 이동',e['id'],(40,768-y-66,982,768-y+4),relative=0,thickness=0)
            # Compact index; full observed values remain in each experiment page.
            short=e['result'].split('. ')[0]+'.'
            text(short,44,y+28,930,14)
            text('모드: '+e['mode']+' | 원문 캡처 '+str(e['capture'])+' | 원문 '+str(e['page'])+'쪽',44,y+52,930,11,'#adbed2')
            y+=68 if len(entries)>6 else 112
        text('미검증: B1(실제 키·작은 세션 예산), C1-300(출력 300) 기록 없음. A7은 별도 수정 브랜치 결과입니다.',44,691,932,11,'#adbed2')
        c.showPage()
        for e in entries:
            base(e['id']+' · '+e['title'],e['mode']+' | 확인하려는 동작과 관측 결과를 원본 캡처에 연결')
            c.bookmarkPage(e['id'])
            c.addOutlineEntry(e['id']+' · '+e['title'],e['id'])
            y=160
            for label,key in [('실험 조건','condition'),('무엇을 확인하나','meaning'),('관측 결과','result')]:
                text(label,42,y,410,16,'#6bd6e8',True);y+=28
                y+=text(e[key],42,y,410,16)+17
            im=capture(e); iw,ih=im.size; scale=min(500/iw,420/ih)
            c.drawImage(ImageReader(im),480+(500-iw*scale)/2,768-164-ih*scale,width=iw*scale,height=ih*scale)
            text('실험 증거: 원문 캡처 '+str(e['capture'])+' / '+str(e['page'])+'쪽 (자르기·내용 보정 없음)',480,594,500,11,'#adbed2')
            text('비용·OpenAI 대조',42,625,940,16,'#70dfb2',True)
            text(e['cost'],42,654,940,14)
            c.showPage()
            if e.get('provider'):
                p=e['provider']
                base(e['id']+' · OpenAI 사용량 대조','앞 페이지의 응답 토큰·비용을 공급자 사용량 화면과 연결합니다.')
                im=capture(p);iw,ih=im.size;scale=min(810/iw,440/ih)
                c.drawImage(ImageReader(im),(1024-iw*scale)/2,768-157-ih*scale,width=iw*scale,height=ih*scale)
                text('공급자 증거: 원문 캡처 '+str(p['capture'])+' / '+str(p['page'])+'쪽. 사용량 화면 원본을 그대로 배치했습니다.',44,608,936,12,'#adbed2')
                text(e['cost'],44,641,936,15)
                c.showPage()
        c.save()
        assert len(PdfReader(path).pages)==count
        print(path.name,count,'pages')
    # Human-editable matching table is the canonical index for all three PDFs.
    lines=['# 실험 제목·결과·비용 증거 매칭표','','원본: '+data['source']+' (2026-10-25). 새 서버 실행·유료 호출 없이 기존 기록을 재편집했습니다.',
           '','각 PDF는 요약 매칭 목록 다음에 실험 조건·확인 의미·관측 결과·원본 캡처·비용을 한 묶음으로 배치합니다.',
           '','| 실험 | 제목·확인 의미 | 모드 | 관측 결과 | 실험 증거 | 비용·OpenAI 대조 |','|---|---|---|---|---|---|']
    for e in data['experiments']:
        cells=[e['id'],e['title']+' / '+e['meaning'],e['mode'],e['result'],f"원문 {e['page']}쪽 캡처 {e['capture']} / [{TITLES[e['guide']]} PDF]({e['guide']}_DARK_TABLET.pdf)",e['cost']]
        lines.append('| '+' | '.join(v.replace('|','/').replace('\n',' ') for v in cells)+' |')
    lines+=['','## 확인 범위','',
      '- V02·V02~V03·C1-16의 확인분: 입력 132, 출력 81토큰, 보고서 단가로 계산한 $0.0000684. OpenAI 화면 Spend $0.00은 반올림 표시입니다.',
      '- 앱 경로의 Guard는 모의입니다. Guard 장부 수치를 실제 OpenAI 청구액에 더하지 않습니다.',
      '- A1~A7은 모의 실험입니다. 장부 0은 해당 모의 실행의 비용 통제 결과이며, 실제 키로 실행한 차단 검증과 다릅니다.',
      '- A7 수정 전·후 예산이 다르고 수정 전 원본 캡처가 없습니다. 개선 결과는 별도 Claude 수정 브랜치 증거이며 현재 작업 소스의 병합 완료로 해석하지 않습니다.',
      '- B1·C1-300은 기록이 없어 미검증입니다. B2의 미증가 관측은 모든 timeout에서 청구가 없다는 보장이 아닙니다.',
      '- 원본 이미지 내용·픽셀을 보존하고 새 페이지에 재배치했습니다. 키·서버 설정은 읽거나 변경하지 않았습니다.',
      '- 편집 원본: EXPERIMENT_RESULT_MAPPING.json. 재생성: python scripts/Build-ExperimentEvidencePdfs.py --source <원본 보고서 PDF 경로>','']
    (OUT/'EXPERIMENT_RESULT_MAPPING.md').write_text('\n'.join(lines),encoding='utf-8')
    (QA/'verification.json').write_text(json.dumps({'sourceSha256':sha256(source.read_bytes()).hexdigest(),'captures':manifest,'newPaidCalls':0},ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':main()
